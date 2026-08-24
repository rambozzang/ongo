package com.ongo.application.portone

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 온라인 결제를 실제로 시작할 수 있는 설정인지 판정한다.
 *
 * ## 왜 네 값을 모두 보는가
 *
 * 예전 `PortOnePaymentService.configured()` 는 store id 와 channel key 가 비었는지만 봤다.
 * 그 둘만 있으면 결제창은 뜨지만, 서버가 결제를 **확정**할 수 없다 — api secret 이 없으면
 * 결제 후 금액·상태 재조회가 안 되고, webhook secret 이 없으면 웹훅 서명을 검증할 수 없다.
 * 결제창까지 갔다가 확정에 실패하는 것이 결제창이 안 뜨는 것보다 나쁘다.
 *
 * ## 왜 운영 기동 검증과 로직이 겹치는가
 *
 * `ProductionConfigurationValidator` 가 같은 판정을 하지만 그것은
 * `onGo-infrastructure` 에 있고 `@Profile("prod")` 로만 뜬다. 애플리케이션 모듈은
 * 인프라 모듈에 의존할 수 없고(의존 방향이 반대다), **이 검사는 프로필과 무관하게 항상
 * 동작해야 한다** — 배포 프로필이 무엇이든 고객을 깨진 결제창으로 보내면 안 되기 때문이다.
 * 그래서 의도적으로 같은 규칙을 이 모듈에 둔다. 규칙이 바뀌면 두 곳을 함께 고쳐야 한다.
 *
 * ## 무엇을 노출하지 않는가
 *
 * 어느 값이 비었는지 돌려주지 않는다. 판정은 boolean 하나다. "webhook secret 이 없다"는
 * 응답은 공격자에게 설정 상태를 알려주는 것이고, 사용자에게는 아무 쓸모가 없다.
 */
@Component
class PortOneReadiness(
    @param:Value("\${payment.portone.store-id:}") private val storeId: String,
    @param:Value("\${payment.portone.channel-key:}") private val channelKey: String,
    @param:Value("\${payment.portone.api-secret:}") private val apiSecret: String,
    @param:Value("\${payment.portone.webhook-secret:}") private val webhookSecret: String,
) {

    /** 네 값이 모두 실제 값일 때만 true. 하나라도 비었거나 placeholder 면 false. */
    fun isReady(): Boolean =
        listOf(storeId, channelKey, apiSecret, webhookSecret).all(::isRealValue)

    /**
     * 두 글자짜리 값도 비어 있지 않다는 검사는 통과한다. 운영 `.env` 가 정확히 그렇게
     * 틀린 적이 있어서, 길이와 대표적인 placeholder 문자열을 함께 본다.
     *
     * 제공자별 키 형식까지 검증하지는 않는다. 그건 제공자의 몫이고, 여기서 흉내 내면
     * 형식이 바뀔 때 멀쩡한 설정을 막게 된다.
     */
    private fun isRealValue(value: String): Boolean {
        val normalized = value.trim().lowercase()
        if (normalized.length < MIN_REAL_VALUE_LENGTH) return false
        return PLACEHOLDER_MARKERS.none(normalized::contains)
    }

    private companion object {
        const val MIN_REAL_VALUE_LENGTH = 8
        val PLACEHOLDER_MARKERS = listOf("dummy", "placeholder", "change-me", "your-", "localhost")
    }
}
