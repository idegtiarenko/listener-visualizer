package co.elastic.elasticsearch.listener.visualizer

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiElement
import com.intellij.ui.components.JBScrollPane
import javax.swing.JComponent

class ActionListenerSpanPopup(val element: PsiElement): DialogWrapper(element.project) {

    init {
        init()
        title = "ActionListeners span"
        isModal = false
    }

    override fun createCenterPanel(): JComponent? = JBScrollPane(ActionListenerSpanVisualizationBuilder.visualize(element))
}
