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

    fun isActionListenerWrapper(signature: String?): Boolean {
        if (signature == null) {
            return false
        }
        return signature.equals("org.elasticsearch.action.ActionListener:runBefore") ||
                signature.equals("org.elasticsearch.action.ActionListener:runAfter") ||
                signature.equals("org.elasticsearch.action.ActionListener:releaseBefore") ||
                signature.equals("org.elasticsearch.action.ActionListener:releaseAfter");
    }

    fun isDelegate(signature: String?): Boolean {
        if (signature == null) {
            return false
        }
        return signature.equals("org.elasticsearch.action.ActionListener:delegateResponse") ||
                signature.equals("org.elasticsearch.action.ActionListener:delegateFailure") ||
                signature.equals("org.elasticsearch.action.ActionListener:delegateFailureAndWrap") ||
                signature.equals("org.elasticsearch.action.ActionListener:delegateFailureIgnoreResponseAndWrap");
    }


}