package co.elastic.elasticsearch.listener.visualizer

import andel.intervals.toReversedList
import co.elastic.elasticsearch.CodeLocation
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.isActionListener
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.isActionListenerWrapper
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.isDelegate
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.shortSignature
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.signature
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiAssignmentExpression
import com.intellij.psi.PsiDeclarationStatement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiParameterList
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.elementType
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
            val signature = signature(call)


            span(element, "passed to ${shortSignature(call)}(..)") {
                if (signature == "org.elasticsearch.action.support.SubscribableListener:addListener") {
                    exploreSubscribeableListener(call, depth - 1)
                    return@span
                }
                if (isActionListener(param)) {
                    exploreFrom(param, depth - 1)
                }
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
                    if (subsListener is PsiLocalVariable) {
                        val initializer = subsListener.initializer
                        if (initializer is PsiMethodCallExpression) {
                            for (initCall in splitCompoundMethodCall(initializer)) {
                                val signature = signature(initCall)
                                if (signature == "org.elasticsearch.action.support.SubscribableListener:andThen") {
                                    // This is a chain of subscribable listeners, we need to explore the prior call
                                    span(initCall.methodExpression, "combined via andThen") {
                                        exploreAndThen(initCall, depth - 1)
                                    }
                                }
                                if (signature == "org.elasticsearch.action.support.SubscribableListener:newForked") {
                                    // This is a chain of subscribable listeners, we need to explore the prior call
                                    span(initCall.methodExpression, "created with newForked") {
                                        exploreLambdaAsArgument(initCall, depth - 1)
                                    }
                                }
                                // andThenApply might be interesting too
                            }
                        }
                    }
                    // Add all mentions of the listener in other places
                    exploreFrom(subsListener, depth - 1)
                    // Walk the chain again and look for andThen calls - this probably needs to be combined with the above
                    // Ideally, we would combine these two, and always parse chained calls, but not yet.
                    for (priorCall in splitCompoundMethodCall(call)) {
                        val signature = signature(priorCall)
                        if (signature == "org.elasticsearch.action.support.SubscribableListener:andThen") {
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

    /**
     * Split compound call into parts.
     */
    private fun splitCompoundMethodCall(call: PsiMethodCallExpression): List<PsiMethodCallExpression> = sequence {
        var current: PsiExpression? = call
        while (current is PsiMethodCallExpression) {
            yield(current)
            current = current.methodExpression.qualifierExpression
        }
    }.toReversedList()

    private fun ActionListenerSpanVisualization.exploreAndThen(
        call: PsiMethodCallExpression,
        depth: Int
    ) {
        exploreLambdaAsArgument(call, depth)
    }

    /**
     * Explore method call that looks like this:
     *  methodCall((listener, args) -> { code using listener })
     * TODO: this is limited to lambda being the only argument, and the listener being the first argument of the lambda.
     * Eventually we will do better.
     */
    private fun ActionListenerSpanVisualization.exploreLambdaAsArgument(
        call: PsiMethodCallExpression,
        depth: Int
    ): Boolean {
        // For now, we assume the listener is always the first argument. Yes, this is wrong.
        val argLambda = call.argumentList.expressions[0]
        if (argLambda is PsiLambdaExpression && argLambda.body != null && argLambda.parameterList.parameters.isNotEmpty()) {
            exploreFrom(argLambda.parameterList.parameters[0], depth - 1)
            return true
        }
        return false
    }

    private fun ActionListenerSpanVisualization.exploreDelegate(delegate: PsiElement, depth: Int, description: String) {
        if (delegate.parent.parent !is PsiMethodCallExpression) {
            return
        }
        val call = delegate.parent.parent as PsiMethodCallExpression
        span(delegate, "resolution delegated") {
            // explore the usage of the delegate
            exploreMethodCall(call, depth - 1)
            // Explore arguments of the call
            if (exploreLambdaAsArgument(call, depth) == false) {
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
