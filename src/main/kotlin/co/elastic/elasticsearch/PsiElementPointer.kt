package co.elastic.elasticsearch

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.util.PsiNavigateUtil

class PsiElementPointer(element: PsiElement) {
    private val pointer = SmartPointerManager.getInstance(element.project).createSmartPsiElementPointer(element)

    fun element(): PsiElement? = pointer.element

    fun navigate() {
        val e = pointer.element
        if (e != null && e.isValid) {
            PsiNavigateUtil.navigate(e)
        } else {
            // TODO navigate once component become invalid
        }
    }
}