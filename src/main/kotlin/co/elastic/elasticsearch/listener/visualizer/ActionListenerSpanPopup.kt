package co.elastic.elasticsearch.listener.visualizer

import co.elastic.elasticsearch.listener.visualizer.ActionListenerFlowPopup.Location
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.isActionListenerWrapper
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.isDelegate
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.shortSignature
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.signature
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiAssignmentExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiParameterList
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
            .sortedBy { Location.from(it.element) }
            .forEach { categorize(it.element, depth) }
    }

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

    private fun ActionListenerSpanVisualization.exploreDelegate(delegate: PsiElement, depth: Int, description: String) {
        if (delegate.parent.parent !is PsiMethodCallExpression) {
            return
        }
        val call = delegate.parent.parent as PsiMethodCallExpression
        span(delegate, "resolution delegated") {
            // explore the usage of the delegate
            exploreMethodCall(call, depth - 1)
            val argLambda = call.argumentList.expressions[0]
            if (argLambda is PsiLambdaExpression && argLambda.body != null && argLambda.parameterList.parameters.isNotEmpty()) {
                // explore delegated result handling
                explore(argLambda.parameterList.parameters[0], depth - 1, description)
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
                "org.elasticsearch.action.ActionListener:delegateResponse" -> exploreDelegate(element, depth - 1, "handles failure")
                "org.elasticsearch.action.ActionListener:delegateFailure" -> exploreDelegate(element, depth - 1, "handles result")
                "org.elasticsearch.action.ActionListener:delegateFailureAndWrap" -> exploreDelegate(element, depth - 1, "handles result with wrap")
                "org.elasticsearch.action.ActionListener:delegateFailureIgnoreResponseAndWrap" -> exploreDelegate(element, depth - 1, "ignores result with wrap")
                "org.elasticsearch.action.ActionListener:map", "org.elasticsearch.action.ActionListener:safeMap" -> {
                    span(element, "is mapped") {
                        categorize(call, depth - 1)
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
