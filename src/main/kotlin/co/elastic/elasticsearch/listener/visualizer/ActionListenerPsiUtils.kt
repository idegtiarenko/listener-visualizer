package co.elastic.elasticsearch.listener.visualizer

import andel.intervals.toReversedList
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLambdaExpression
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

    fun shortSignature(call: PsiMethodCallExpression): String {
        if (call.methodExpression.reference == null) {
            return "unknown"
        }
        val method = call.methodExpression.reference!!.resolve() as PsiMethod
        return "${method.containingClass?.name}:${method.name}"
    }

    fun methodSignature(method: PsiMethod): String {
        return "${method.containingClass?.qualifiedName}:${method.name}(${method.parameterList.parameters.joinToString { it.type.canonicalText }})"
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
        // TODO: moar methods from ActionListener
        return signature.equals("org.elasticsearch.action.ActionListener:runBefore") ||
                signature.equals("org.elasticsearch.action.ActionListener:runAfter") ||
                signature.equals("org.elasticsearch.action.ActionListener:releaseBefore") ||
                signature.equals("org.elasticsearch.action.ActionListener:releaseAfter") ||
                signature.equals("org.elasticsearch.action.ActionListener:run");
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

    /**
     * For patterns like methodCall((listener, args) -> { .. }) extracts listener
     */
    fun unwrapLambda(call: PsiMethodCallExpression): PsiParameter? {
        // For now, we assume the listener is always the first argument. Yes, this is wrong.
        val firstArgument = call.argumentList.expressions.first()
        if (firstArgument is PsiLambdaExpression
            && firstArgument.body != null
            && firstArgument.parameterList.parameters.isNotEmpty()) {
            return firstArgument.parameterList.parameters.first()
        } else {
            return null
        }
    }

    /**
     * Split chain calls (like something.a().b().c()) into a list of [a(),b(),c()]
     */
    fun callChain(call: PsiMethodCallExpression): List<PsiMethodCallExpression> = sequence {
        var current: PsiExpression? = call
        while (current is PsiMethodCallExpression) {
            yield(current)
            current = current.methodExpression.qualifierExpression
        }
    }.toReversedList()
}