package co.elastic.elasticsearch.listener.visualizer

import co.elastic.elasticsearch.icons.Icons
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.PsiNavigateUtil
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
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

        override fun createCenterPanel(): JComponent? {
            return JPanel(BorderLayout()).apply {
                add(Tree(PsiElementTreeNode(project, element)).apply {
                    addMouseListener(object: MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent) {
                            val element = (getPathForLocation(e.x, e.y)?.lastPathComponent as PsiElementTreeNode).element()
                            if (element != null && element.isValid) {
                                PsiNavigateUtil.navigate(element)
                            } else {
                                // TODO navigate once component become invalid
                                logger<ListenerLineMarkerProvider>().info("Element is invalidated")
                            }
                        }
                    })
                }, BorderLayout.CENTER)
            }
        }
    }

    class PsiElementTreeNode(project: Project?, element: PsiElement) : DefaultMutableTreeNode() {
        private val pointer: SmartPsiElementPointer<PsiElement> =
            SmartPointerManager.getInstance(project).createSmartPsiElementPointer(element)

        fun element(): PsiElement?  = pointer.element

        override fun toString(): String {
            return pointer.element?.text ?: "<Invalidated>"
        }
    }
}