package com.ongo.application.ai.economics

import com.ongo.common.enums.AiProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** 설정에 저장되는 제공자. 제공하지 않는 제공자는 저장 단계에서 QWEN 으로 바뀐다. */
class AiProviderOfferTest {

    @Test
    @DisplayName("예전 기본값 CLAUDE 와 비싼 제공자는 QWEN 으로 저장된다")
    fun premiumBecomesQwen() {
        for (name in listOf("CLAUDE", "claude", "GEMINI", "OPENAI", "KIMI", "GLM", "없는값", null)) {
            assertEquals(AiProvider.QWEN, AiProvider.offeredOrDefault(name as String?), "$name")
        }
    }

    @Test
    @DisplayName("제공 목록의 제공자는 그대로 저장된다")
    fun offeredIsKept() {
        assertEquals(AiProvider.MINIMAX, AiProvider.offeredOrDefault("minimax"))
        assertEquals(AiProvider.QWEN, AiProvider.offeredOrDefault(AiProvider.QWEN))
    }
}
