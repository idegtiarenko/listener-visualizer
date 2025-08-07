package co.elastic.elasticsearch.listener.visualizer

import co.elastic.elasticsearch.listener.visualizer.ActionListenerPsiUtils.isActionListener
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionUpdateThreadAware
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

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
            ActionListenerSpanPopup(element).show()
        }
    }
}