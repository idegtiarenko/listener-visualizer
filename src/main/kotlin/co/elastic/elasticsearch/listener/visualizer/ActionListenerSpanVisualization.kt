package co.elastic.elasticsearch.listener.visualizer

import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.ui.components.JBPanel
import com.intellij.util.PsiNavigateUtil
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent

class ActionListenerSpanVisualization {

    private val component = ActionListenerViewPanel()

    private var offset: Int = 0
    private var depth: Int = 0

    private var maxDepth: Int = HEIGHT

    fun span(element: PsiElement, description: String, children: () -> Unit) {
        val startOffset = offset
        val startDepth = depth
        depth += HEIGHT
        maxDepth += HEIGHT
        children()
        depth -= HEIGHT
        component.add(PointerButton(element, AllIcons.Debugger.Frame, description).apply {
            setBounds(startOffset, startDepth, offset - startOffset, HEIGHT)
        })
    }

    fun addResponse(element: PsiElement, description: String) {
        component.add(PointerButton(element, AllIcons.Status.Success, description).apply {
            setBounds(offset, depth, WIDTH, HEIGHT)
        })
        offset += WIDTH
    }

    fun addFailure(element: PsiElement, description: String) {
        component.add(PointerButton(element, AllIcons.General.Error, description).apply {
            setBounds(offset, depth, WIDTH, HEIGHT)
        })
        offset += WIDTH
    }

    fun addInfo(element: PsiElement, description: String) {
        component.add(PointerButton(element, AllIcons.General.Information, description).apply {
            setBounds(offset, depth, WIDTH, HEIGHT)
        })
        offset += WIDTH
    }

    fun addWarning(element: PsiElement, description: String) {
        component.add(PointerButton(element, AllIcons.General.Warning, description).apply {
            setBounds(offset, depth, WIDTH, HEIGHT)
        })
        offset += WIDTH
    }

    fun build(): JComponent = component

    inner class ActionListenerViewPanel : JBPanel<ActionListenerViewPanel>(null) {
        override fun getPreferredSize(): Dimension = Dimension(offset, maxDepth)
    }

    private class PointerButton(element: PsiElement, icon: Icon, description: String) : JButton(description) {
        private val pointer = PsiElementPointer(element)
        init {
            this.icon = icon
            addActionListener { pointer.navigate() }
        }
    }

    private class PsiElementPointer(element: PsiElement) {
        private val pointer = SmartPointerManager.getInstance(element.project).createSmartPsiElementPointer(element)

        fun navigate() {
            val e = pointer.element
            if (e != null && e.isValid) {
                PsiNavigateUtil.navigate(e)
            } else {
                // TODO navigate once component become invalid
            }
        }
    }

    companion object {
        const val WIDTH = 150
        const val HEIGHT = 25
    }
}