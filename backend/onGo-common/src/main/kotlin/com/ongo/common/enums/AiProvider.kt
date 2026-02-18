package com.ongo.common.enums

enum class AiProvider(val displayName: String) {
    CLAUDE("Claude"),
    GEMINI("Gemini"),
    OPENAI("OpenAI"),
    ;

    companion object {
        fun fromString(value: String?): AiProvider =
            try {
                valueOf(value?.uppercase() ?: "CLAUDE")
            } catch (_: IllegalArgumentException) {
                CLAUDE
            }
    }
}
