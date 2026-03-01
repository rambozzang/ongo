package com.ongo.common.enums

enum class AiProvider(val displayName: String) {
    CLAUDE("Claude"),
    GEMINI("Gemini"),
    OPENAI("OpenAI"),
    QWEN("Qwen 3.5"),
    KIMI("Kimi K2.5"),
    GLM("GLM-5"),
    MINIMAX("MiniMax M2.5"),
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
