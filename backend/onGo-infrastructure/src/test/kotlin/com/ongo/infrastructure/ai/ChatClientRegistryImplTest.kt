package com.ongo.infrastructure.ai

import com.ongo.common.enums.AiProvider
import io.mockk.mockk
import org.springframework.ai.chat.client.ChatClient
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatClientRegistryImplTest {
    private val client = mockk<ChatClient>()

    @Test
    fun `dummy and short keys do not make providers available`() {
        val registry = registry(
            anthropicApiKey = "dummy-anthropic-key",
            openAiApiKey = "12",
            dashScopeApiKey = "",
        )

        assertFalse(registry.isProviderAvailable(AiProvider.CLAUDE))
        assertFalse(registry.isProviderAvailable(AiProvider.OPENAI))
        assertFalse(registry.isProviderAvailable(AiProvider.QWEN))
    }

    @Test
    fun `a real key makes only its provider family available`() {
        val registry = registry(
            anthropicApiKey = "valid-anthropic-key",
            openAiApiKey = "",
            dashScopeApiKey = "",
        )

        assertTrue(registry.isProviderAvailable(AiProvider.CLAUDE))
        assertFalse(registry.isProviderAvailable(AiProvider.OPENAI))
        assertFalse(registry.isProviderAvailable(AiProvider.QWEN))
    }

    @Test
    fun `dashscope key enables its explicitly supported model clients`() {
        val registry = registry(dashScopeApiKey = "valid-dashscope-key")

        assertTrue(registry.isProviderAvailable(AiProvider.QWEN))
        assertTrue(registry.isProviderAvailable(AiProvider.KIMI))
        assertTrue(registry.isProviderAvailable(AiProvider.GLM))
        assertTrue(registry.isProviderAvailable(AiProvider.MINIMAX))
    }

    private fun registry(
        anthropicApiKey: String = "",
        openAiApiKey: String = "",
        geminiApiKey: String = "",
        dashScopeApiKey: String = "",
    ) = ChatClientRegistryImpl(
        anthropicChatClient = client,
        openaiChatClient = client,
        geminiChatClient = null,
        qwenChatClient = client,
        kimiChatClient = client,
        glmChatClient = client,
        minimaxChatClient = client,
        anthropicApiKey = anthropicApiKey,
        openAiApiKey = openAiApiKey,
        geminiApiKey = geminiApiKey,
        dashScopeApiKey = dashScopeApiKey,
    )
}
