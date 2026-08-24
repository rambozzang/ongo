package com.ongo.application.portone

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 결제 준비 판정.
 *
 * 이 판정 하나가 체크아웃 차단과 화면의 결제 CTA 노출을 동시에 정한다. 여기서 느슨하면
 * 고객이 깨진 결제창까지 간다.
 */
class PortOneReadinessTest {

    private fun readiness(
        storeId: String = "store-abc12345",
        channelKey: String = "channel-abc12345",
        apiSecret: String = "apisecret-abc12345",
        webhookSecret: String = "webhook-abc12345",
    ) = PortOneReadiness(storeId, channelKey, apiSecret, webhookSecret)

    @Test
    fun `네 값이 모두 실제 값이면 준비된 것으로 본다`() {
        assertTrue(readiness().isReady())
    }

    /*
     * 예전 판정은 store id 와 channel key 만 봤다. 그 둘만 있으면 결제창은 뜨지만 서버가
     * 결제를 확정하지 못한다 — 결제창까지 갔다가 실패하는 것이 더 나쁘다.
     */
    @Test
    fun `네 값 중 하나라도 비면 준비되지 않은 것으로 본다`() {
        assertFalse(readiness(storeId = "").isReady(), "store id 누락이 통과했다")
        assertFalse(readiness(channelKey = "").isReady(), "channel key 누락이 통과했다")
        assertFalse(readiness(apiSecret = "").isReady(), "api secret 누락이 통과했다")
        assertFalse(readiness(webhookSecret = "").isReady(), "webhook secret 누락이 통과했다")
    }

    @Test
    fun `공백만 있는 값도 준비되지 않은 것으로 본다`() {
        assertFalse(readiness(apiSecret = "        ").isReady())
    }

    /* 두 글자짜리 값도 "비어 있지 않다"는 검사는 통과한다. 운영 .env 가 그렇게 틀린 적이 있다. */
    @Test
    fun `8자 미만은 준비되지 않은 것으로 본다`() {
        assertFalse(readiness(storeId = "1234567").isReady(), "7자가 통과했다")
        assertTrue(readiness(storeId = "12345678").isReady(), "8자 경계가 막혔다")
    }

    @Test
    fun `대표적인 placeholder 는 대소문자 무관하게 거른다`() {
        listOf(
            "dummy-store-value",
            "PLACEHOLDER-VALUE",
            "change-me-please",
            "your-store-id",
            "http://localhost:8080",
        ).forEach { value ->
            assertFalse(readiness(channelKey = value).isReady(), "placeholder 가 통과했다: $value")
        }
    }

    /* 판정은 boolean 하나다. 어느 값이 빠졌는지 알려주면 설정 상태가 새어나간다. */
    @Test
    fun `판정 결과에 어떤 값이 문제인지 담기지 않는다`() {
        val result: Boolean = readiness(apiSecret = "").isReady()

        assertFalse(result)
    }
}
