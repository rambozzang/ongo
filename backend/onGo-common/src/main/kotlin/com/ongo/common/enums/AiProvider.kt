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
        /**
         * **사용자에게 제공하는 제공자.** 크레딧 1개의 원가 예산(₩2.2) 안에서 쓸 만한 답을 내는 저가 모델만 둔다.
         *
         * Claude·Gemini·OpenAI 채팅은 출력 단가가 Qwen 의 4~6배, Kimi·GLM 은 입력 단가가 2배를 넘는다.
         * 크레딧은 제공자와 무관하게 같으므로, 가장 작은 기능(2크레딧, 원가 예산 ₩4.4)에 평범한 입력을 넣으면
         * 그 모델들은 쓸 만한 답을 살 수 없다 — 가로채기가 출력을 크게 줄이거나 거부한다.
         * 순서가 곧 대체 순서다 — 가장 싼 것부터. `AiUnitEconomicsTest` 가 이 목록 전부를 검사한다.
         */
        val OFFERED: List<AiProvider> = listOf(QWEN, MINIMAX)

        fun fromString(value: String?): AiProvider =
            try {
                valueOf(value?.uppercase() ?: "QWEN")
            } catch (_: IllegalArgumentException) {
                QWEN
            }

        /** 저장·실행용. 제공하지 않는 제공자(예전 기본값 CLAUDE 등)는 QWEN 으로 바꾼다. */
        fun offeredOrDefault(value: String?): AiProvider = fromString(value).takeIf { it in OFFERED } ?: QWEN
        fun offeredOrDefault(value: AiProvider?): AiProvider = value?.takeIf { it in OFFERED } ?: QWEN
    }
}
