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

    /* ---- 런타임 의존성 누락 ---- */

    /*
     * 빈이 없다는 것은 "판단할 수 없다" 는 뜻이지 "쓸 수 있다" 가 아니다.
     *
     * 예전에는 null 이면 활성으로 떨어뜨리고 주석으로 "단위 테스트뿐" 이라고 가정했는데,
     * 그 가정을 강제하는 장치가 없었다. 조건부 빈 등록이나 프로필 실수 하나로 프런트가
     * 유료 AI·UGC·결제를 정상으로 표시하고, 사용자는 클릭한 뒤에야 실패를 본다.
     *
     * 세 기능을 각각 고정한다. 하나만 검사하면 나머지 둘이 조용히 열린 채로 남는다.
     */

    @Test
    fun `AI capability is disabled when the provider registry is missing`() {
        val capability = CapabilityUseCase().list().first { it.key == "ai" }

        assertFalse(capability.enabled, "AI 의존성이 없는데 활성으로 열렸다")
        assertEquals("AI 기능을 지금 사용할 수 없습니다. 관리자에게 문의해 주세요.", capability.reason)
    }

    @Test
    fun `shorts render capability is disabled when the renderer is missing`() {
        val capability = CapabilityUseCase().list().first { it.key == "ugc/shorts/runs" }

        assertFalse(capability.enabled, "렌더러 의존성이 없는데 활성으로 열렸다")
        assertEquals("영상 렌더링을 지금 사용할 수 없습니다. 관리자에게 문의해 주세요.", capability.reason)
    }

    @Test
    fun `payment capability is disabled when the readiness check is missing`() {
        val capability = CapabilityUseCase().list().first { it.key == "payment" }

        assertFalse(capability.enabled, "결제 의존성이 없는데 활성으로 열렸다")
        assertEquals(
            "온라인 결제를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도하거나 고객지원에 문의해 주세요.",
            capability.reason,
        )
    }

    /*
     * 의존성이 전부 빠진 최악의 배포에서도 구독 화면은 남아야 한다. 결제를 시작하지
     * 못할 뿐, 자기 플랜과 크레딧 잔액을 못 보게 만들 이유는 없다.
     */
    @Test
    fun `missing dependencies never hide the subscription screen`() {
        val capabilities = CapabilityUseCase().list()

        assertTrue(capabilities.first { it.key == "subscription" }.enabled)
        assertEquals(null, capabilities.first { it.key == "subscription" }.reason)
    }

    /* 의존성이 없다고 해서 관련 없는 기능까지 잠그면 배포 하나가 서비스 전체를 멈춘다. */
    @Test
    fun `missing dependencies only disable the features that need them`() {
        val disabled = CapabilityUseCase().list().filterNot { it.enabled }.map { it.key }.toSet()

        assertEquals(setOf("ai", "ugc/shorts/runs", "payment"), disabled)
    }

    /*
     * 같은 키가 두 번 나가면 이 목록은 계약이 아니라 우연이 된다.
     *
     * 프런트는 키로 조회한다. 중복된 두 항목의 활성 상태가 갈리면 어느 쪽이 이기는지는
     * 조회 방식(`find` 인지 맵 생성인지)에 달리고, 그 차이는 화면마다 다르다. 목록에
     * 기능을 추가하다 `competitors` 가 두 번 들어간 적이 있어 여기서 고정한다.
     */
    @Test
    fun `capability keys are listed only once`() {
        val keys = CapabilityUseCase().list().map { it.key }
        val duplicated = keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys

        assertTrue(duplicated.isEmpty(), "중복된 capability 키: $duplicated")
    }

    /* 어느 빈이 빠졌는지는 사용자에게 쓸모가 없고 배포 구성만 드러낸다. */
    @Test
    fun `missing dependency reasons never name an internal component`() {
        val reasons = CapabilityUseCase().list().filterNot { it.enabled }.mapNotNull { it.reason }

        listOf("bean", "registry", "renderer", "ffmpeg", "portone", "null", "client").forEach { term ->
            assertFalse(
                reasons.any { it.lowercase().contains(term) },
                "이유 문구에 내부 구성요소가 노출됐다: $term ($reasons)",
            )
        }
    }
}
