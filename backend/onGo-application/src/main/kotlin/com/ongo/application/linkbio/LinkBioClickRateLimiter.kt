package com.ongo.application.linkbio

import com.github.benmanes.caffeine.cache.Caffeine
import com.ongo.common.exception.BusinessException
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 공개 링크 클릭 집계의 남용 상한.
 *
 * ## 무엇을 막는가
 *
 * `POST /api/v1/linkbio/public/{slug}/links/{linkId}/click` 은 인증이 없다
 * (`SecurityConfig` 의 permitAll). `slug` 는 공개 URL 에 그대로 있고 `linkId` 는 공개 페이지
 * 응답에 실려 있으므로, 누구나 두 값을 얻어 클릭 수를 **무제한으로** 올릴 수 있었다.
 * 그 숫자는 `getAnalytics` 를 통해 크리에이터에게 성과로 보고된다 — 외부 입력으로 만들어진
 * 가짜 지표다.
 *
 * ## 왜 IP 가 아니라 링크로 세는가
 *
 * IP 별로 세는 것이 자연스러워 보이지만 **이 서버에서는 안전하게 할 수 없다.**
 *
 *  - `AuthRateLimiter` 처럼 `HttpServletRequest.remoteAddr` 을 쓰면, nginx 뒤에 있는 이
 *    애플리케이션에는 모든 요청이 `127.0.0.1` 로 보인다. 버킷 하나를 전 사용자가 공유하게
 *    되어, 공격자 한 명이 정상 방문자 전부를 막는다 — 문제보다 나쁜 해결이다.
 *  - `X-Forwarded-For` 를 믿으면 공격자가 요청마다 다른 값을 넣어 제한을 그대로 통과한다.
 *    헤더는 프록시가 덮어써 주기 전에는 **사용자 입력**이다.
 *  - 이를 고치려면 `server.forward-headers-strategy` 와 신뢰 프록시 설정이 필요한데,
 *    그건 인증·CORS·리다이렉트 URL 에 함께 영향을 주는 전역 변경이라 이 범위가 아니다.
 *
 * 그래서 **링크 하나가 단위 시간에 받을 수 있는 클릭 수**에 상한을 둔다. 출처를 묻지 않으므로
 * 프록시 설정에도 헤더 위조에도 영향받지 않고, 스키마 변경도 필요 없다.
 *
 * ## 집계 기준 (이 상한이 정하는 사실)
 *
 *  - 허용된 클릭은 **1회로 센다.** 같은 방문자의 반복 클릭도 상한 안에서는 그대로 센다 —
 *    믿을 수 있는 방문자 식별자가 없는 상태에서 "같은 사람"을 판정하면 그 판정 자체가 추측이 된다.
 *  - 상한을 넘은 클릭은 **세지 않는다.** 세지 못한 것을 센 것처럼 만들지 않으며, 호출자에게
 *    오류로 알린다.
 *  - 따라서 이 값은 "실제 클릭 수" 가 아니라 **"우리가 신뢰할 수 있는 클릭 수의 하한"** 이다.
 *
 * ## 상한을 넉넉히 잡은 이유
 *
 * [CAPACITY_PER_MINUTE] 는 링크-in-bio 한 개가 유기적으로 받을 수 있는 속도보다 훨씬 위다.
 * 조이면 정말로 화제가 된 링크의 성과를 우리가 깎아 보고하게 된다 — 가짜를 막으려다
 * 진짜를 지우는 셈이다. 반대로 이 정도만 되어도 자동화 남용은 초당 수천에서 분당 수백으로
 * 떨어져 실효가 있다.
 */
@Component
class LinkBioClickRateLimiter {

    /**
     * 링크별 버킷. 만료를 두어 오래 안 눌린 링크가 메모리에 남지 않게 하고, 최대 개수로
     * 존재하지 않는 `linkId` 를 무작위로 두드리는 요청이 힙을 밀어내지 못하게 한다.
     */
    private val buckets = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(10))
        .maximumSize(100_000)
        .build<Long, Bucket>()

    /**
     * 이 링크의 클릭을 한 번 집계해도 되는지 판정한다.
     *
     * @throws LinkBioClickRateLimitExceededException 상한을 넘었을 때. 호출자는 집계를
     *   **하지 않아야 한다.**
     */
    fun checkClickRateLimit(linkId: Long) {
        val bucket = buckets.get(linkId) { createBucket() }
        if (!bucket.tryConsume(1)) {
            throw LinkBioClickRateLimitExceededException()
        }
    }

    private fun createBucket(): Bucket {
        val bandwidth = Bandwidth.builder()
            .capacity(CAPACITY_PER_MINUTE)
            .refillGreedy(CAPACITY_PER_MINUTE, Duration.ofMinutes(1))
            .build()
        return Bucket.builder().addLimit(bandwidth).build()
    }

    private companion object {
        /** 링크 하나가 1분에 집계할 수 있는 클릭 수. 유기적 상한보다 크고 자동화보다는 작다. */
        const val CAPACITY_PER_MINUTE = 300L
    }
}

/**
 * 기존 오류 규약을 그대로 쓴다 — `BusinessException` 은 `GlobalExceptionHandler` 가
 * 400 과 안정적인 `error` 코드로 내보낸다(`AuthRateLimitExceededException` 과 같은 방식).
 *
 * 메시지에 상한값이나 남은 횟수를 넣지 않는다. 공격자에게 조절 정보를 주는 것이고,
 * 정상 방문자는 이 응답을 보지 않는다 — 프런트가 클릭 기록 실패를 삼키고 링크 이동은
 * `<a>` 의 기본 동작으로 그대로 진행된다.
 */
class LinkBioClickRateLimitExceededException : BusinessException(
    "LINKBIO_CLICK_RATE_LIMIT_EXCEEDED",
    "클릭 집계 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.",
)
