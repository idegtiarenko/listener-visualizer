package co.elastic.elasticsearch.listener.visualizer

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class SampleAction: AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        HelloDialog(e.project).show()
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