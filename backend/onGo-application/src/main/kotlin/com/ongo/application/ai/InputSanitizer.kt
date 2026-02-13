package com.ongo.application.ai

object InputSanitizer {

    fun sanitize(input: String): String {
        return "<user_input>${input}</user_input>"
    }
}
