package co.elastic.elasticsearch.listener.visualizer

import co.elastic.elasticsearch.icons.Icons
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiElement
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class ListenerLineMarkerProvider: LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (ListenerDetector.isListener(element)) {
            return LineMarkerInfo<PsiElement>(
                element,
                element.textRange,
                Icons.ES,
                { psi -> "Implements ActionListener" },
                { event, psi ->
                    HelloDialog(element.project).show()
                },
                GutterIconRenderer.Alignment.LEFT
            )
        }
        return null
    }

    class HelloDialog(p: Project?): DialogWrapper(p) {

        init {
            init()
            setTitle("Hello dialog")
        }

        override fun createCenterPanel(): JComponent? {
            val panel = JPanel(BorderLayout())
            panel.add(JLabel("Hello dialog"), BorderLayout.CENTER)
            return panel
        }
    }
}