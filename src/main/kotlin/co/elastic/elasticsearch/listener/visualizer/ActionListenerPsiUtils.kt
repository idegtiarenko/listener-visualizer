package co.elastic.elasticsearch.listener.visualizer

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiParameter
import com.intellij.psi.util.InheritanceUtil

object ActionListenerPsiUtils {

    fun signature(call: PsiMethodCallExpression): String {
        if (call.methodExpression.reference == null) {
            return "unknown"
        }
        val method = call.methodExpression.reference!!.resolve() as PsiMethod
        return "${method.containingClass?.qualifiedName}:${method.name}"
    }

    fun isActionListener(element: PsiElement): Boolean {
        return when (element) {
            is PsiParameter -> InheritanceUtil.isInheritor(element.type, "org.elasticsearch.action.ActionListener")
            is PsiLocalVariable -> InheritanceUtil.isInheritor(element.type, "org.elasticsearch.action.ActionListener")
            is PsiField -> InheritanceUtil.isInheritor(element.type, "org.elasticsearch.action.ActionListener")
            else -> false
        }
    }
}