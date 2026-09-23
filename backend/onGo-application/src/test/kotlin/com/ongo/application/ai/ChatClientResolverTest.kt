package com.ongo.application.ai

import com.ongo.common.enums.AiProvider
import com.ongo.common.exception.BusinessException
import com.ongo.domain.settings.UserSettings
import com.ongo.domain.settings.UserSettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.ai.chat.client.ChatClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 제공자 선택. **제공 목록(AiProvider.OFFERED — 크레딧 원가 예산 안에서 쓸 만한 답을 내는 저가 모델) 밖으로는
 * 절대 나가지 않는다.** 예전에는 대체 순서가 Claude → OpenAI → Gemini 였고, 설정이 없는 사용자와 예전 기본값
 * CLAUDE 를 가진 사용자가 모두 가장 비싼 모델로 갔다.
 */
class ChatClientResolverTest {
    private val registry = mockk<ChatClientRegistry>()
    private val settings = mockk<UserSettingsRepository>()
    private val qwen = mockk<ChatClient>()
    private val minimax = mockk<ChatClient>()

    @Test
    fun `requested unavailable provider falls back to a configured offered provider`() {
        every { settings.findByUserId(7L) } returns UserSettings(userId = 7L, defaultAiProvider = AiProvider.QWEN)
        every { registry.isProviderAvailable(AiProvider.QWEN) } returns false
        every { registry.isProviderAvailable(AiProvider.MINIMAX) } returns true
        every { registry.getClient(AiProvider.MINIMAX) } returns minimax

        assertEquals(minimax, ChatClientResolver(registry, settings).resolve(7L))
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

    /** 예전 기본값 CLAUDE 를 가진 사용자. Claude 키가 있어도 쓰지 않는다. */
    @Test
    fun `a stored premium provider is never used even when configured`() {
        every { settings.findByUserId(7L) } returns UserSettings(userId = 7L, defaultAiProvider = AiProvider.CLAUDE)
        every { registry.isProviderAvailable(any()) } returns true
        every { registry.getClient(AiProvider.QWEN) } returns qwen

        assertEquals(qwen, ChatClientResolver(registry, settings).resolve(7L))
        verify(exactly = 0) { registry.getClient(AiProvider.CLAUDE) }
    }

    /** 저가 모델 키가 없으면 비싼 모델로 새지 않고 멈춘다 — 조용히 원가가 5배가 되는 것보다 낫다. */
    @Test
    fun `only premium providers configured means no provider rather than a costly fallback`() {
        every { registry.isProviderAvailable(AiProvider.QWEN) } returns false
        every { registry.isProviderAvailable(AiProvider.MINIMAX) } returns false
        every { registry.isProviderAvailable(AiProvider.CLAUDE) } returns true
        every { registry.isProviderAvailable(AiProvider.OPENAI) } returns true

        val error = assertFailsWith<BusinessException> { ChatClientResolver(registry, settings).resolve(0L) }

        assertEquals("AI_PROVIDER_NOT_CONFIGURED", error.code)
    }

    @Test
    fun `system user uses the cheapest configured offered provider`() {
        every { registry.isProviderAvailable(AiProvider.QWEN) } returns true
        every { registry.getClient(AiProvider.QWEN) } returns qwen

        assertEquals(qwen, ChatClientResolver(registry, settings).resolve(0L))
    }
}
