package co.elastic.elasticsearch.listener.visualizer

import co.elastic.elasticsearch.icons.Icons
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.PsiNavigateUtil
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.tree.DefaultMutableTreeNode

class ListenerLineMarkerProvider: LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (ListenerDetector.isListener(element)) {
            return LineMarkerInfo<PsiElement>(
                element,
                element.textRange,
                Icons.ES,
                { psi -> "Implements ActionListener" },
                { event, psi ->
                    HelloDialog(element.project, psi).show()
                },
                GutterIconRenderer.Alignment.LEFT
            )
        }
        return null
    }

    class HelloDialog(val project: Project?, val element: PsiElement): DialogWrapper(project) {

        init {
            init()
            title = "ActionListeners flow"
            isModal = false
        }

        override fun createCenterPanel(): JComponent {
            return JBScrollPane(Tree(explore(element)).apply {
                addMouseListener(object: MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        val element = (getPathForLocation(e.x, e.y)?.lastPathComponent as ListenerTreeNode).element()
                        if (element != null && element.isValid) {
                            PsiNavigateUtil.navigate(element)
                        } else {
                            // TODO navigate once component become invalid
                            logger<ListenerLineMarkerProvider>().info("Element is invalidated")
                        }
                    }
                })
            })
        }

        private fun explore(element: PsiElement): ListenerTreeNode {
            val root = ListenerTreeNode(element)
            ReferencesSearch.search(element).findAll()
                .map { categorize(it.element) }
                .sortedWith (compareBy({it.location.file}, {it.location.line}))
                .forEachIndexed { i, node ->
                    root.insert(node, i)
                }
            return root
        }

        private fun categorize(element: PsiElement, depth: Int = 0): ListenerTreeNode {
            if (element.parent.parent is PsiMethodCallExpression) {
                val call = element.parent.parent as PsiMethodCallExpression
                val methodName = call.methodExpression.referenceName
                return when (methodName) {
                    "onResponse" -> ListenerTreeNode(element, description = "resolved with a result")
                    "onFailure" -> ListenerTreeNode(element, description = "resolved with failure")
                    else -> ListenerTreeNode(element, description = "Passed as a parameter to ${methodName}(..) call")
                }
            }
            return ListenerTreeNode(element)
        }
    }

    class ListenerTreeNode(
        element: PsiElement,
        val description: String = ""
    ) : DefaultMutableTreeNode() {

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