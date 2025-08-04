package co.elastic.elasticsearch.listener.visualizer

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil

object ListenerDetector {

    fun isListener(element: PsiElement): Boolean {
        // TODO register for leaf elements
        return when (element) {
            is PsiParameter if isActionListener(PsiTreeUtil.findChildOfType(element, PsiJavaCodeReferenceElement::class.java)?.resolve()) -> true
            is PsiReferenceExpression if isActionListener(element.resolve()) -> true
            else -> false
        }
    }

    private fun isActionListener(element: PsiElement?): Boolean {
        return when (element) {
            is PsiClass -> InheritanceUtil.isInheritor(element, "org.elasticsearch.action.ActionListener")
            is PsiLocalVariable -> InheritanceUtil.isInheritor(element.type, "org.elasticsearch.action.ActionListener")
            is PsiParameter -> InheritanceUtil.isInheritor(element.type, "org.elasticsearch.action.ActionListener")
            else -> false
        }
    }
}