package co.elastic.elasticsearch.listener.visualizer

import co.elastic.elasticsearch.CodeLocation
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.SmartPointerManager
import com.intellij.ui.components.JBPanel
import com.intellij.util.PsiNavigateUtil
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import kotlin.math.max

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
        val button = JButton(description, AllIcons.Debugger.Frame).withNavigationTo(element, description)
        val width = max(offset - startOffset, button.desiredWidth())
        offset = startOffset + width
        button.setBounds(startOffset, startDepth, width, HEIGHT)
        component.add(button)
    }

    fun addResponse(element: PsiElement, description: String) = add(element, AllIcons.Status.Success, description)
    fun addFailure(element: PsiElement, description: String) = add(element, AllIcons.General.Error, description)
    fun addInfo(element: PsiElement, description: String) = add(element, AllIcons.General.Information, description)
    fun addWarning(element: PsiElement, description: String) = add(element, AllIcons.General.Warning, description)

    fun build(): JComponent = component

    inner class ActionListenerViewPanel: JBPanel<ActionListenerViewPanel>(null) {
        override fun getPreferredSize(): Dimension = Dimension(offset, maxDepth)
    }

    private fun add(element: PsiElement, icon: Icon, description: String) {
        val button = JButton(description, icon).withNavigationTo(element, description)
        val width = button.desiredWidth()
        button.setBounds(offset, depth, width, HEIGHT)
        component.add(button)
        offset += width
    }

    private fun JButton.desiredWidth(): Int = getFontMetrics(font).stringWidth(text) + 35// icon and border margin

    private fun JButton.withNavigationTo(element: PsiElement, description: String): JButton {
        val pointer = PsiElementPointer(element)
        addActionListener { pointer.navigate() }
        if (element is PsiReferenceExpression) {
            toolTipText = "${element.text} $description at ${CodeLocation.from(element)}"
        } else {
            toolTipText = "$description at ${CodeLocation.from(element)}"
        }
        return this
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
        private const val HEIGHT = 25
    }
}