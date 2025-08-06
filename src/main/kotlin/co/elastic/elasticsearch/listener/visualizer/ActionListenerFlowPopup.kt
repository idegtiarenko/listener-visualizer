package co.elastic.elasticsearch.listener.visualizer

import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.signature
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiParameterList
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.childrenOfType
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.PsiNavigateUtil
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
                    val element = (getPathForLocation(e.x, e.y)?.lastPathComponent as ListenerTreeNode).element()
                    if (element != null && element.isValid) {
                        PsiNavigateUtil.navigate(element)
                    } else {
                        // TODO navigate once component become invalid
                        logger<ActionListenerFlowPopup>().info("Element is invalidated")
                    }
                }
            })
        })
    }

    private fun explore(element: PsiElement, depth: Int, description: String): ListenerTreeNode {
        val root = ListenerTreeNode(element, description)
        ReferencesSearch.search(element).findAll()
            .map { categorize(it.element, depth) }
            .sortedWith (compareBy({it.location.file}, {it.location.line}))
            .forEachIndexed { i, node ->
                root.insert(node, i)
            }
        return root
    }

    private fun categorize(element: PsiElement, depth: Int): ListenerTreeNode {
        // TODO detect and prevent recursion
        if (element.parent.parent is PsiMethodCallExpression) {
            val call = element.parent.parent as PsiMethodCallExpression
            val signature = signature(call)
            return when (signature) {
                "org.elasticsearch.action.ActionListener:onResponse" -> ListenerTreeNode(element, "resolved with a result")
                "org.elasticsearch.action.ActionListener:onFailure" -> ListenerTreeNode(element, "resolved with failure")
                "org.elasticsearch.action.ActionListener:delegateResponse" -> {
                    ListenerTreeNode(element, "delegates response").apply {
                        insert(categorize(call, depth - 1), 0)
                    }
                }
                "org.elasticsearch.action.ActionListener:delegateFailure" -> {
                    ListenerTreeNode(element, "delegates failure").apply {
                        insert(categorize(call, depth - 1), 0)
                    }
                }
                "org.elasticsearch.action.ActionListener:delegateFailureAndWrap" -> {
                    ListenerTreeNode(element, "delegates and wraps failure").apply {
                        insert(categorize(call, depth - 1), 0)
                    }
                }
                "org.elasticsearch.action.ActionListener:map" -> {
                    ListenerTreeNode(element, "is mapped").apply {
                        insert(categorize(call, depth - 1), 0)
                    }
                }
                else -> {
                    val methodName = call.methodExpression.referenceName
                    val paramIndex = call.argumentList.expressions.indexOf(element)
                    val target = call.methodExpression.resolve()
                    if (target != null && paramIndex != -1 && depth >= 0) {
                        val param = target.childrenOfType<PsiParameterList>()[0].parameters[paramIndex]
                        ListenerTreeNode(element, "passed as an argument to $methodName(..) call").apply {
                            insert(explore(param, depth - 1, "passed as ${param.name} in $methodName(..)"), 0)
                        }
                    } else {
                        ListenerTreeNode(element, "passed as an argument to $methodName(..) call")
                    }
                }
            }
        }
        return ListenerTreeNode(element, "non analyzed")
    }

    class ListenerTreeNode(element: PsiElement, val description: String) : DefaultMutableTreeNode() {

        val location: Location = Location.from(element)
        private val pointer: SmartPsiElementPointer<PsiElement> =
            SmartPointerManager.getInstance(element.project).createSmartPsiElementPointer(element)

        fun element(): PsiElement?  = pointer.element

        override fun toString(): String {
            return if (pointer.element?.isValid?:false) describe(pointer.element!!) else "<Invalidated>"
        }
        private fun describe(element: PsiElement): String = "${element.text} $description at $location"
    }

    class Location(val file: String, val line: Int) {
        companion object {
            fun from(element: PsiElement): Location {
                val file = element.containingFile.virtualFile
                val line = FileDocumentManager.getInstance().getDocument(file)?.getLineNumber(element.textOffset)?:-1
                return Location(file.name, line)
            }
        }
        override fun toString(): String = "$file:$line"
    }
}