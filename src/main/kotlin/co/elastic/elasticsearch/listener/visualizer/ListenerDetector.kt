package co.elastic.elasticsearch.listener.visualizer

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil

object ListenerDetector {

    fun isListener(element: PsiElement): Boolean {
        val parent: PsiElement? = element.parent
        if (element is PsiIdentifier) {
            return when (parent) {
                is PsiParameter -> isActionListener(
                    PsiTreeUtil.findChildOfType(
                        parent,
                        PsiJavaCodeReferenceElement::class.java
                    )?.resolve()
                )

                is PsiReferenceExpression -> isActionListener(parent.resolve())
                else -> false
            }
        }
        return false
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