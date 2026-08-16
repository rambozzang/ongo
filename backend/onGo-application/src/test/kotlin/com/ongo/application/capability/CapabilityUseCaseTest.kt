package com.ongo.application.capability

import com.ongo.application.ai.ChatClientRegistry
import com.ongo.common.enums.AiProvider
import com.ongo.domain.ugc.shorts.RendererAvailability
import com.ongo.domain.ugc.shorts.VideoRenderer
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CapabilityUseCaseTest {
    @Test
    fun `AI capability is disabled when no real provider is available`() {
        val registry = mockk<ChatClientRegistry>()
        every { registry.isProviderAvailable(any()) } returns false

        val capability = CapabilityUseCase(chatClientRegistry = registry)
            .list()
            .first { it.key == "ai" }

        assertFalse(capability.enabled)
        assertEquals("사용 가능한 AI 제공자 API 키가 설정되지 않았습니다.", capability.reason)
    }

    @Test
    fun `AI capability is enabled when at least one provider is available`() {
        val registry = mockk<ChatClientRegistry>()
        every { registry.isProviderAvailable(any()) } answers {
            firstArg<AiProvider>() == AiProvider.CLAUDE
        }

        val capability = CapabilityUseCase(chatClientRegistry = registry)
            .list()
            .first { it.key == "ai" }

        assertTrue(capability.enabled)
        assertEquals(null, capability.reason)
    }

    @Test
    fun `shorts render capability exposes the renderer availability reason`() {
        val renderer = mockk<VideoRenderer>()
        every { renderer.checkAvailability() } returns RendererAvailability(
            available = false,
            reason = "영상 렌더러가 설치되지 않았습니다.",
        )

        val capability = CapabilityUseCase(videoRenderer = renderer)
            .list()
            .first { it.key == "ugc/shorts/runs" }

        assertFalse(capability.enabled)
        assertEquals("영상 렌더러가 설치되지 않았습니다.", capability.reason)
    }
}
