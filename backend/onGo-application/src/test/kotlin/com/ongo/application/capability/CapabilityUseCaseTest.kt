package com.ongo.application.capability

import com.ongo.application.ai.ChatClientRegistry
import com.ongo.application.portone.PortOneReadiness
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

    private fun readiness(webhookSecret: String) = PortOneReadiness(
        storeId = "store-abc12345",
        channelKey = "channel-abc12345",
        apiSecret = "apisecret-abc12345",
        webhookSecret = webhookSecret,
    )

    /*
     * 화면이 결제 CTA 를 감출 근거다. 이 신호가 없으면 사용자는 결제창을 열고 나서야
     * 실패를 본다.
     */
    @Test
    fun `payment capability is disabled when PortOne configuration is not ready`() {
        val capability = CapabilityUseCase(portOneReadiness = readiness(webhookSecret = ""))
            .list()
            .first { it.key == "payment" }

        assertFalse(capability.enabled)
        assertEquals(
            "온라인 결제를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도하거나 고객지원에 문의해 주세요.",
            capability.reason,
        )
    }

    /* 어느 설정이 빠졌는지는 사용자에게 쓸모가 없고 설정 상태만 노출한다. */
    @Test
    fun `disabled payment reason never names a secret field`() {
        val reason = CapabilityUseCase(portOneReadiness = readiness(webhookSecret = ""))
            .list()
            .first { it.key == "payment" }
            .reason
            .orEmpty()

        listOf("secret", "store", "channel", "webhook", "api-secret").forEach { term ->
            assertFalse(reason.lowercase().contains(term), "이유 문구에 설정 이름이 노출됐다: $term")
        }
    }

    @Test
    fun `payment capability is enabled when PortOne configuration is ready`() {
        val capability = CapabilityUseCase(portOneReadiness = readiness(webhookSecret = "webhook-abc12345"))
            .list()
            .first { it.key == "payment" }

        assertTrue(capability.enabled)
        assertEquals(null, capability.reason)
    }

    /* 결제를 막아도 구독 화면은 보여야 한다. 플랜·크레딧 잔액 조회까지 막을 이유가 없다. */
    @Test
    fun `disabling payment does not hide the subscription screen`() {
        val capabilities = CapabilityUseCase(portOneReadiness = readiness(webhookSecret = "")).list()

        assertTrue(capabilities.first { it.key == "subscription" }.enabled)
    }
}
