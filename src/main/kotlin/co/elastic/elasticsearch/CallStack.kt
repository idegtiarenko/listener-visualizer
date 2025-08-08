package co.elastic.elasticsearch

import java.util.LinkedList

class CallStack {

    private val stack = LinkedList<String>()

    fun tryPush(signature: String): Boolean {
        if (!stack.contains(signature)) {
            stack.push(signature)
            return true
        } else {
            return false
        }
    }

    fun pop() {
        stack.pop()
    }
}