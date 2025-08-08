package co.elastic.elasticsearch.listener.visualizer

import co.elastic.elasticsearch.CallStack
import co.elastic.elasticsearch.CodeLocation
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.callChain
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.isActionListenerWrapper
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.isDelegate
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.methodSignature
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.shortSignature
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.signature
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.unwrapLambda
import com.intellij.psi.PsiAssignmentExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiParameterList
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.parentOfType
import javax.swing.JComponent

class ActionListenerSpanVisualizationBuilder {

    companion object {
        fun visualize(element: PsiElement): JComponent = ActionListenerSpanVisualizationBuilder().run {
            element.parentOfType<PsiMethod>()?.apply {
                stack.tryPush(methodSignature(this))
            }
            viz.span(element, "inspected") {
                exploreReferences(element)
            }
            viz.build()
        }
    }

    private val viz = ActionListenerSpanVisualization()
    private val stack = CallStack()

    private fun categorize(element: PsiElement) {
        if (element.parent.parent is PsiMethodCallExpression) {
            val call = element.parent.parent as PsiMethodCallExpression
            val signature = signature(call)
            when (signature) {
                "org.elasticsearch.action.ActionListener:onResponse" -> viz.addResponse(element, "responds")
                "org.elasticsearch.action.ActionListener:onFailure" -> viz.addFailure(element, "fails")
                "org.elasticsearch.action.ActionListener:delegateResponse" -> exploreDelegate(element, "handles failure")
                "org.elasticsearch.action.ActionListener:delegateFailure" -> exploreDelegate(element, "handles result")
                "org.elasticsearch.action.ActionListener:delegateFailureAndWrap" -> exploreDelegate(element, "handles result with wrap")
                "org.elasticsearch.action.ActionListener:delegateFailureIgnoreResponseAndWrap" -> exploreDelegate(element, "ignores result with wrap")
                "org.elasticsearch.action.ActionListener:map", "org.elasticsearch.action.ActionListener:safeMap" -> {
                    viz.span(element, "is mapped") {
                        categorize(call)
                    }
                }
                "org.elasticsearch.action.support.SubscribableListener:andThen" -> {
                    viz.span(element, "combined via andThen") {
                        exploreAndThen(call)
                    }
                }
                "org.elasticsearch.action.support.SubscribableListener:addListener" -> exploreSubscribableListener(call)
                else if !isActionListenerWrapper(signature) -> {
                    exploreMethodCall(element)
                }
            }
        }
        if (element.parent is PsiAssignmentExpression) {
            val assignment = element.parent as PsiAssignmentExpression
            if (assignment.rExpression is PsiMethodCallExpression) {
                val call = assignment.rExpression as PsiMethodCallExpression
                val signature = signature(call)
                if (isDelegate(signature)) {
                    // This is a delegate, we handle it elsewhere
                    return
                }
                if (isActionListenerWrapper(signature)) {
                    val target = assignment.lExpression
                    val source = call.argumentList.expressions[0]
                    if (source == target) {
                        viz.addInfo(target, "is wrapped by " + signature.substringAfterLast("."))
                    } else {
                        viz.addInfo(element, "re-assigned")
                    }
                }
            } else {
                viz.addInfo(element, "re-assigned")
            }
        }
    }

    /**
     * Find all references to the element and attach them to the root node.
     * The references are categorized based on the method call signature.
     */
    private fun exploreReferences(element: PsiElement) {
        ReferencesSearch.search(element).findAll()
            .sortedBy { CodeLocation.from(it.element) }
            .forEach { categorize(it.element) }
    }

    /**
     * This method call uses listener element as a parameter, explore it
     */
    private fun exploreMethodCall(element: PsiElement) {
        if (element.parent.parent !is PsiMethodCallExpression) {
            return
        }
        val call = element.parent.parent as PsiMethodCallExpression
        val paramIndex = call.argumentList.expressions.indexOf(element)
        val target = call.methodExpression.resolve()
        if (target != null && target is PsiMethod && paramIndex != -1) {
            val param = target.childrenOfType<PsiParameterList>()[0].parameters[paramIndex]
            if (stack.tryPush(methodSignature(target))) {
                viz.span(param, "passed to ${shortSignature(call)}(..)") {
                    exploreReferences(param)
                }
                stack.pop()
            } else {
                viz.addInfo(element, "passed recursively to ${shortSignature(call)}(..)")
            }
        } else {
            viz.addInfo(element, "passed to ${shortSignature(call)}(..)")
        }
    }

    /**
     * We found a SubscribableListener, probably via addListener(), explore it.
     */
    private fun exploreSubscribableListener(call: PsiMethodCallExpression) {
        // If the call is a SubscribableListener, we need to find the source of the listener
        // Find the object that is being the target of the call
        var target = call.methodExpression.qualifierExpression
        while (target is PsiMethodCallExpression) {
            target = target.methodExpression.qualifierExpression
        }
        // For now we only handle this simple case. Though the infrastructure would likely allow for more complex cases.
        if (target is PsiReferenceExpression) {
            val subsListener = target.resolve()
            if (subsListener != null) {
                viz.span(subsListener, "subscribes to async action") {
                    // Handle the initial creation expression
                    if (subsListener is PsiLocalVariable && subsListener.initializer is PsiMethodCallExpression) {
                        for (initCall in callChain(subsListener.initializer as PsiMethodCallExpression)) {
                            when (signature(initCall)) {
                                "org.elasticsearch.action.support.SubscribableListener:newForked" -> {
                                    viz.span(initCall.methodExpression, "created with newForked") {
                                        exploreAndThen(initCall)
                                    }
                                }
                                "org.elasticsearch.action.support.SubscribableListener:andThen" -> {
                                    viz.span(initCall.methodExpression, "combined via andThen") {
                                        exploreAndThen(initCall)
                                    }
                                }
                            }
                            // TODO andThenApply might be interesting too
                        }
                    }
                    // Add all mentions of the listener in other places
                    exploreReferences(subsListener)
                    // Walk the chain again and look for andThen calls - this probably needs to be combined with the above
                    // Ideally, we would combine these two, and always parse chained calls, but not yet.
                    for (priorCall in callChain(call)) {
                        when (signature(priorCall)) {
                            "org.elasticsearch.action.support.SubscribableListener:andThen" -> {
                                // This is a chain of subscribable listeners, we need to explore the prior call
                                viz.span(priorCall.methodExpression, "combined via andThen") {
                                    exploreAndThen(priorCall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Explore method call that looks like this:
     *  methodCall((listener, args) -> { code using listener })
     * TODO: this is limited to lambda being the only argument, and the listener being the first argument of the lambda.
     * Eventually we will do better.
     */
    private fun exploreAndThen(call: PsiMethodCallExpression) {
        unwrapLambda(call)?.apply { exploreReferences(this) }
    }

    private fun exploreDelegate(delegate: PsiElement, description: String) {
        if (delegate.parent.parent !is PsiMethodCallExpression) {
            return
        }
        val call = delegate.parent.parent as PsiMethodCallExpression
        viz.span(delegate, "resolution delegated") {
            // explore the usage of the delegate
            exploreMethodCall(call)
            val nested = unwrapLambda(call)
            if (nested != null) {
                // explore delegated result handling
                viz.span(nested, description) {
                    exploreReferences(nested)
                }
            } else {
                viz.addInfo(delegate, "delegates")
            }
        }
    }
}