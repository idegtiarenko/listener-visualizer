package co.elastic.elasticsearch.listener.visualizer

import co.elastic.elasticsearch.CodeLocation
import co.elastic.elasticsearch.PsiElementPointer
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.isActionListener
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.isActionListenerWrapper
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.isDelegate
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.signature
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.*
import com.intellij.psi.javadoc.PsiDocTagValue
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.childrenOfType
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.tree.DefaultMutableTreeNode

class ActionListenerFlowPopup(val element: PsiElement): DialogWrapper(element.project) {

    init {
        init()
        title = "ActionListeners flow"
        isModal = false
    }


    override fun createCenterPanel(): JComponent? {
        return JBScrollPane(Tree(explore(element, 5, "inspected")).apply {
            addMouseListener(object: MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    (getPathForLocation(e.x, e.y)?.lastPathComponent as ListenerTreeNode).pointer.navigate()
                }
            })
        })
    }

    private fun explore(element: PsiElement, depth: Int, description: String): ListenerTreeNode {
        val root = ListenerTreeNode(element, description)
        return exploreFrom(element, root, depth)
    }

    /**
     * Find all references to the element and attach them to the root node.
     * The references are categorized based on the method call signature.
     */
    private fun exploreFrom(element: PsiElement, root: ListenerTreeNode, depth: Int): ListenerTreeNode {
        ReferencesSearch.search(element).findAll()
            .mapNotNull { categorize(it.element, depth) }
            .sortedWith(compareBy({ it.location.file }, { it.location.line }))
            .forEachIndexed { i, node ->
                root.insert(node, i)
            }
        return root
    }

    private fun exploreMethodCall(element: PsiElement, depth: Int, replacementText: String? = null): ListenerTreeNode? {
        val call = element.parent.parent as? PsiMethodCallExpression ?: return null
        val methodName = call.methodExpression.referenceName
        val paramIndex = call.argumentList.expressions.indexOf(element)
        val target = call.methodExpression.resolve()
        if (target != null && paramIndex != -1 && depth >= 0) {
            val param = target.childrenOfType<PsiParameterList>()[0].parameters[paramIndex]
            val passNode = ListenerTreeNode(element, "passed as an argument to $methodName(..) call", replacementText)
            if (isActionListener(param)) {
                passNode.apply {
                    insert(explore(param, depth - 1, "passed as ${param.name} in $methodName(..)"), 0)
                }
            }
            return passNode
        } else {
            return ListenerTreeNode(element, "passed as an argument to $methodName(..) call", replacementText)
        }
    }

    private fun exploreDelegate(delegate: PsiElement, depth: Int, description: String): ListenerTreeNode {
        // Delegate structure: listener.delegateFailure((l, indexResolution) -> { code using l })
        // We need to find "l" and explore the code with l as the element
        val root = ListenerTreeNode(delegate, description)
        val call = delegate.parent.parent as PsiMethodCallExpression
        val argLambda = call.argumentList.expressions[0]
        if (argLambda is PsiLambdaExpression && argLambda.body != null) {
            // Find the first parameter of the lambda, which is the listener
            val lambdaParams = argLambda.parameterList.parameters
            if (lambdaParams.isNotEmpty()) {
                val listenerParam = lambdaParams[0]
                // Explore the body of the lambda with the new listener parameter
                // TODO: can we rename it so it reports "listener" instead of "l"?
                val delegateNode = exploreFrom(listenerParam, root, depth)
                // If the call itself is an argument to another method call, and that method call receives a listener, then we need to explore that as well
                val passNode = exploreMethodCall(call, depth, delegate.text)
                if (passNode != null) {
                    delegateNode.insert(passNode, delegateNode.childCount)
                }
                return delegateNode
            }
        }
        return root
    }

    private fun categorizeAssignment(element: PsiElement): ListenerTreeNode? {
        val assignment = element.parent as? PsiAssignmentExpression ?: return null
        val defaultNode = ListenerTreeNode(element, "re-assigned")
        val call = assignment.rExpression as? PsiMethodCallExpression ?: return defaultNode
        val signature = signature(call)
        if (isDelegate(signature)) {
            // This is a delegate, we handle it elsewhere
            return null
        }

        if (isActionListenerWrapper(signature)) {
            val assigned = assignment.lExpression
            val listener = call.argumentList.expressions[0]
            if (listener.text.equals(assigned.text)) {
                // This is a wrapper indeed
                return ListenerTreeNode(assigned, "is wrapped by " + signature.substringAfterLast("."))
            }
        }

        return defaultNode
    }

    private fun categorize(element: PsiElement, depth: Int): ListenerTreeNode? {
        // TODO detect and prevent recursion
        if (element.parent.parent is PsiMethodCallExpression) {
            val call = element.parent.parent as PsiMethodCallExpression
            val signature = signature(call)
            return when (signature) {
                "org.elasticsearch.action.ActionListener:onResponse" -> ListenerTreeNode(
                    element,
                    "resolved with a result"
                )

                "org.elasticsearch.action.ActionListener:onFailure" -> ListenerTreeNode(
                    element,
                    "resolved with failure"
                )

                "org.elasticsearch.action.ActionListener:delegateResponse" -> {
                    exploreDelegate(element, depth - 1, "handles failure")
                }

                "org.elasticsearch.action.ActionListener:delegateFailure" -> {
                    exploreDelegate(element, depth - 1, "handles result")
                }

                "org.elasticsearch.action.ActionListener:delegateFailureAndWrap" -> {
                    exploreDelegate(element, depth - 1, "handles result with wrap")
                }

                "org.elasticsearch.action.ActionListener:delegateFailureIgnoreResponseAndWrap" -> {
                    exploreDelegate(element, depth - 1, "ignores result with wrap")
                }

                "org.elasticsearch.action.ActionListener:map", "org.elasticsearch.action.ActionListener:safeMap" -> {
                    ListenerTreeNode(element, "is mapped").apply {
                        insert(categorize(call, depth - 1), 0)
                    }
                }

                else -> {
                    if (isActionListenerWrapper(signature)) {
                        return null
                    }
                    exploreMethodCall(element, depth)
                }
            }
        }

        if (element.parent is PsiAssignmentExpression) {
            return categorizeAssignment(element)
        }

        if (element is PsiDocTagValue) {
            // Ignore doc references for now
            return null
        }

        return ListenerTreeNode(element, "non analyzed")
    }

    class ListenerTreeNode(element: PsiElement, val description: String, val replacementText: String? = null):
        DefaultMutableTreeNode() {

        val location: CodeLocation = CodeLocation.from(element)
        val pointer: PsiElementPointer = PsiElementPointer(element)

        override fun toString(): String {
            return ApplicationManager.getApplication().runReadAction<String> {
                if (pointer.element()?.isValid ?: false) describe(pointer.element()!!) else "<Invalidated>"
            }
        }

        private fun describe(element: PsiElement): String =
            "${replacementText ?: element.text} $description at $location"
    }
}