package co.elastic.elasticsearch.listener.visualizer

import co.elastic.elasticsearch.icons.Icons
import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.isActionListener
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager


class AnalyzeActionListenerSpanAction: AnAction(), ActionUpdateThreadAware {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val element = e.getData(CommonDataKeys.PSI_ELEMENT)
        e.getPresentation().setEnabledAndVisible(element != null && isActionListener(element))
    }

    override fun actionPerformed(e: AnActionEvent) {
        val element = e.getData(CommonDataKeys.PSI_ELEMENT)
        if (element != null && isActionListener(element)) {
            val spans = JBScrollPane(ActionListenerSpanVisualizationBuilder.visualize(element))
            val toolWindow = getOrCreateToolWindow("ActionListeners", element.project)
            toolWindow.contentManager.addAndSelect(ContentFactory.getInstance().createContent(spans, element.text, false))
            toolWindow.show()
        }
    }

    private fun getOrCreateToolWindow(id: String, project: Project): ToolWindow {
        var toolWindow: ToolWindow? = ToolWindowManager.getInstance(project).getToolWindow(id)
        if (toolWindow == null) {
            toolWindow = ToolWindowManager.getInstance(project).registerToolWindow(id) {
                icon = Icons.ES
                anchor = ToolWindowAnchor.BOTTOM
            }
        }
        return toolWindow
    }

    private fun ContentManager.addAndSelect(content: Content) {
        addContent(content, 0)
        setSelectedContent(content)
    }
}