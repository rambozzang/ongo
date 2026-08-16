package com.ongo.application.ai

import com.ongo.common.enums.AiProvider
import com.ongo.common.exception.BusinessException
import com.ongo.domain.settings.UserSettings
import com.ongo.domain.settings.UserSettingsRepository
import io.mockk.every
import io.mockk.mockk
import org.springframework.ai.chat.client.ChatClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChatClientResolverTest {
    private val registry = mockk<ChatClientRegistry>()
    private val settings = mockk<UserSettingsRepository>()
    private val claude = mockk<ChatClient>()
    private val openAi = mockk<ChatClient>()

    @Test
    fun `requested unavailable provider falls back to a configured provider`() {
        every { settings.findByUserId(7L) } returns UserSettings(userId = 7L, defaultAiProvider = AiProvider.QWEN)
        every { registry.isProviderAvailable(AiProvider.QWEN) } returns false
        every { registry.isProviderAvailable(AiProvider.CLAUDE) } returns true
        every { registry.getClient(AiProvider.CLAUDE) } returns claude

        val result = ChatClientResolver(registry, settings).resolve(7L)

        assertEquals(claude, result)
    }

    @Test
    fun `resolver fails explicitly when no provider is configured`() {
        every { settings.findByUserId(7L) } returns null
        every { registry.isProviderAvailable(any()) } returns false

        val error = assertFailsWith<BusinessException> {
            ChatClientResolver(registry, settings).resolve(7L)
        }

        assertEquals("AI_PROVIDER_NOT_CONFIGURED", error.code)
    }

    @Test
    fun `system user also uses a configured provider instead of assuming qwen`() {
        every { registry.isProviderAvailable(AiProvider.CLAUDE) } returns false
        every { registry.isProviderAvailable(AiProvider.OPENAI) } returns true
        every { registry.getClient(AiProvider.OPENAI) } returns openAi

        val result = ChatClientResolver(registry, settings).resolve(0L)

        assertEquals(openAi, result)
    }
}
