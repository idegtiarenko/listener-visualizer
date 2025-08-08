package co.elastic.elasticsearch

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiElement

class CodeLocation(val file: String, val line: Int): Comparable<CodeLocation> {
    companion object {
        fun from(element: PsiElement): CodeLocation {
            val file = element.containingFile.virtualFile
            val line =
                FileDocumentManager.getInstance().getDocument(file)?.getLineNumber(element.textOffset) ?: -2
            // getLineNumber is 0-based
            return CodeLocation(file.name, line + 1)
        }
    }

    override fun compareTo(other: CodeLocation): Int = compareValuesBy(this, other, { it.file }, { it.line })
    override fun toString(): String = "$file:$line"
}