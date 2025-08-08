package co.elastic.elasticsearch.listener.visualizer

import co.elastic.elasticsearch.CodeLocation
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.callChain
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.isActionListenerWrapper
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.isDelegate
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.shortSignature
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.signature
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.unwrapLambda
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.childrenOfType
import com.intellij.ui.components.JBScrollPane
import javax.swing.JComponent

class ActionListenerSpanPopup(val element: PsiElement): DialogWrapper(element.project) {

    init {
        init()
        title = "ActionListeners span"
        isModal = false
    }

    override fun createCenterPanel(): JComponent? = JBScrollPane(ActionListenerSpanVisualization().run {
        explore(element, 10, "inspected")
        build()
    })

    private fun ActionListenerSpanVisualization.explore(element: PsiElement, depth: Int, description: String) {
        span(element, description) {
            exploreFrom(element, depth - 1)
        }
    }

    /**
     * Find all references to the element and attach them to the root node.
     * The references are categorized based on the method call signature.
     */
    private fun ActionListenerSpanVisualization.exploreFrom(element: PsiElement, depth: Int) {
        ReferencesSearch.search(element).findAll()
            .sortedBy { CodeLocation.from(it.element) }
            .forEach { categorize(it.element, depth) }
    }


    /**
     * This method call uses listener element as a parameter, explore it
     */
    private fun ActionListenerSpanVisualization.exploreMethodCall(element: PsiElement, depth: Int) {
        if (element.parent.parent !is PsiMethodCallExpression) {
            return
        }
        val call = element.parent.parent as PsiMethodCallExpression
        val paramIndex = call.argumentList.expressions.indexOf(element)
        val target = call.methodExpression.resolve()
        if (target != null && paramIndex != -1 && depth >= 0) {
            val param = target.childrenOfType<PsiParameterList>()[0].parameters[paramIndex]
            span(element, "passed to ${shortSignature(call)}(..)") {
                exploreFrom(param, depth - 1)
            }
        } else {
            span(element, "passed to ${shortSignature(call)}(..)") {
                // uncategorized usage
            }
        }
    }

    /**
     * We found a subscribeable listener, probably via addListener(), explore it.
     */
    private fun ActionListenerSpanVisualization.exploreSubscribeableListener(
        call: PsiMethodCallExpression,
        depth: Int
    ) {
        // If the call is a subscribeable listener, we need to find the source of the listener
        // Find the object that is being the target of the call
        var target = call.methodExpression.qualifierExpression
        while (target is PsiMethodCallExpression) {
            target = target.methodExpression.qualifierExpression
        }
        // For now we only handle this simple case. Though the infrastructure would likely allow for more complex cases.
        if (target is PsiReferenceExpression) {
            val subsListener = target.resolve()
            if (subsListener != null) {
                span(subsListener, "subscribable listener") {
                    // Handle the initial creation expression
                    if (subsListener is PsiLocalVariable && subsListener.initializer is PsiMethodCallExpression) {
                        for (initCall in callChain(subsListener.initializer as PsiMethodCallExpression)) {
                            when (signature(initCall)) {
                                "org.elasticsearch.action.support.SubscribableListener:newForked" -> {
                                    span(initCall.methodExpression, "created with newForked") {
                                        exploreAndThen(initCall, depth - 1)
                                    }
                                }
                                "org.elasticsearch.action.support.SubscribableListener:andThen" -> {
                                    span(initCall.methodExpression, "combined via andThen") {
                                        exploreAndThen(initCall, depth - 1)
                                    }
                                }
                            }
                            // TODO andThenApply might be interesting too
                        }
                    }
                    // Add all mentions of the listener in other places
                    exploreFrom(subsListener, depth - 1)
                    // Walk the chain again and look for andThen calls - this probably needs to be combined with the above
                    // Ideally, we would combine these two, and always parse chained calls, but not yet.
                    for (priorCall in callChain(call)) {
                        when (signature(priorCall)) {
                            "org.elasticsearch.action.support.SubscribableListener:andThen" -> {
                                // This is a chain of subscribable listeners, we need to explore the prior call
                                span(priorCall.methodExpression, "combined via andThen") {
                                    exploreAndThen(priorCall, depth - 1)
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
    private fun ActionListenerSpanVisualization.exploreAndThen(call: PsiMethodCallExpression, depth: Int) {
        val nested = unwrapLambda(call)
        if (nested != null) {
            exploreFrom(nested, depth - 1)
        }
    }

    private fun ActionListenerSpanVisualization.exploreDelegate(delegate: PsiElement, depth: Int, description: String) {
        if (delegate.parent.parent !is PsiMethodCallExpression) {
            return
        }
        val call = delegate.parent.parent as PsiMethodCallExpression
        span(delegate, "resolution delegated") {
            // explore the usage of the delegate
            exploreMethodCall(call, depth - 1)
            val nested = unwrapLambda(call)
            if (nested != null) {
                // explore delegated result handling
                explore(nested, depth - 1, description)
            } else {
                addInfo(delegate, "delegates")
            }
        }
    }

    private fun ActionListenerSpanVisualization.categorize(element: PsiElement, depth: Int) {
        if (element.parent.parent is PsiMethodCallExpression) {
            val call = element.parent.parent as PsiMethodCallExpression
            val signature = signature(call)
            when (signature) {
                "org.elasticsearch.action.ActionListener:onResponse" -> addResponse(element, "responds")
                "org.elasticsearch.action.ActionListener:onFailure" -> addFailure(element, "fails")
                "org.elasticsearch.action.ActionListener:delegateResponse" -> exploreDelegate(
                    element,
                    depth - 1,
                    "handles failure"
                )

                "org.elasticsearch.action.ActionListener:delegateFailure" -> exploreDelegate(
                    element,
                    depth - 1,
                    "handles result"
                )

                "org.elasticsearch.action.ActionListener:delegateFailureAndWrap" -> exploreDelegate(
                    element,
                    depth - 1,
                    "handles result with wrap"
                )

                "org.elasticsearch.action.ActionListener:delegateFailureIgnoreResponseAndWrap" -> exploreDelegate(
                    element,
                    depth - 1,
                    "ignores result with wrap"
                )

                "org.elasticsearch.action.ActionListener:map", "org.elasticsearch.action.ActionListener:safeMap" -> {
                    span(element, "is mapped") {
                        categorize(call, depth - 1)
                    }
                }

                "org.elasticsearch.action.support.SubscribableListener:andThen" -> {
                    span(element, "combined via andThen") {
                        exploreAndThen(call, depth - 1)
                    }
                }
                "org.elasticsearch.action.support.SubscribableListener:addListener" -> {
                    exploreSubscribeableListener(call, depth - 1)
                }

                else if !isActionListenerWrapper(signature) -> {
                    exploreMethodCall(element, depth)
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
                        addInfo(target, "is wrapped by " + signature.substringAfterLast("."))
                    } else {
                        addInfo(element, "re-assigned")
                    }
                }
            } else {
                addInfo(element, "re-assigned")
            }
        }
    }
}
