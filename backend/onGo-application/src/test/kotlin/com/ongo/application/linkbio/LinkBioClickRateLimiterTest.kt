package com.ongo.application.linkbio

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

/**
 * 공개 클릭 집계 상한의 **경계**를 고정한다.
 *
 * ## 무엇을 막는 장치인가
 *
 * `POST /linkbio/public/{slug}/links/{linkId}/click` 은 인증이 없고, `slug` 와 `linkId` 는
 * 둘 다 공개 응답에서 얻을 수 있다. 상한이 없으면 누구나 크리에이터의 클릭 수를 무제한으로
 * 올릴 수 있고, 그 숫자는 성과 화면에 그대로 보고된다.
 *
 * ## 왜 경계를 고정하는가
 *
 * "막힌다" 만 재면 상한을 1 로 낮춰도 통과한다. 그러면 정상 방문자의 두 번째 클릭부터
 * 집계가 사라진다 — 가짜를 막으려다 진짜를 지우는 방향의 회귀이고, 조용해서 알아채기 어렵다.
 * 그래서 **정확히 상한까지는 통과하고 그 다음 하나가 막힌다**를 함께 고정한다.
 */
class LinkBioClickRateLimiterTest {

    /** `LinkBioClickRateLimiter.CAPACITY_PER_MINUTE` 와 같아야 한다. */
    private val capacityPerMinute = 300

    @Test
    @DisplayName("상한까지는 모두 통과한다 — 화제가 된 링크의 성과를 깎지 않는다")
    fun allowsEveryClickUpToCapacity() {
        val limiter = LinkBioClickRateLimiter()

        repeat(capacityPerMinute) { limiter.checkClickRateLimit(1L) }
        // 예외 없이 여기까지 오면 통과다.
    }

    @Test
    @DisplayName("상한 바로 다음 클릭은 막는다")
    fun rejectsTheClickJustPastCapacity() {
        val limiter = LinkBioClickRateLimiter()
        repeat(capacityPerMinute) { limiter.checkClickRateLimit(1L) }

        val error = assertThrows<LinkBioClickRateLimitExceededException> {
            limiter.checkClickRateLimit(1L)
        }

        assertEquals("LINKBIO_CLICK_RATE_LIMIT_EXCEEDED", error.code)
    }

    /**
     * **버킷은 링크마다 따로여야 한다.**
     *
     * 하나로 합치면 인기 있는 링크 하나가 같은 페이지의 다른 링크까지 막는다. 더 나쁜 것은
     * 공격자가 자기 링크를 두드려 **남의 링크 집계를 멈출 수 있다**는 점이다.
     */
    @Test
    @DisplayName("한 링크가 상한에 닿아도 다른 링크는 영향받지 않는다")
    fun exhaustingOneLinkDoesNotBlockAnother() {
        val limiter = LinkBioClickRateLimiter()
        repeat(capacityPerMinute) { limiter.checkClickRateLimit(1L) }
        assertThrows<LinkBioClickRateLimitExceededException> { limiter.checkClickRateLimit(1L) }

        // 다른 링크는 자기 몫을 그대로 갖는다.
        repeat(capacityPerMinute) { limiter.checkClickRateLimit(2L) }
        assertThrows<LinkBioClickRateLimitExceededException> { limiter.checkClickRateLimit(2L) }
    }

    /**
     * 상한을 넘은 뒤에도 **계속 막혀야 한다.**
     *
     * 버킷을 만들 때마다 새로 만드는 구현(캐시 키를 잘못 잡는 등)이면 두 번째 호출부터
     * 다시 통과한다. 그러면 상한이 있으나 마나다.
     */
    @Test
    @DisplayName("상한을 넘은 뒤 반복 호출도 계속 막힌다")
    fun keepsRejectingAfterCapacityIsGone() {
        val limiter = LinkBioClickRateLimiter()
        repeat(capacityPerMinute) { limiter.checkClickRateLimit(1L) }

        repeat(5) {
            assertThrows<LinkBioClickRateLimitExceededException> { limiter.checkClickRateLimit(1L) }
        }
    }

    /** 오류 메시지에 상한값·잔여 횟수를 노출하지 않는다 — 공격자에게 조절 정보를 준다. */
    @Test
    @DisplayName("오류 메시지에 상한값이나 잔여 횟수를 담지 않는다")
    fun errorMessageLeaksNoTuningInformation() {
        val limiter = LinkBioClickRateLimiter()
        repeat(capacityPerMinute) { limiter.checkClickRateLimit(1L) }

        val message = assertThrows<LinkBioClickRateLimitExceededException> {
            limiter.checkClickRateLimit(1L)
        }.message.orEmpty()

        for (leaked in listOf("300", "bucket", "capacity", "남은")) {
            kotlin.test.assertFalse(leaked in message, "메시지에 조절 정보가 있다: $message")
        }
    }
}
