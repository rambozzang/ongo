package com.ongo.application.ai

import com.github.benmanes.caffeine.cache.Caffeine
import com.ongo.common.exception.BusinessException
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class AiRateLimiter {

    private val buckets = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(10))
        .maximumSize(10_000)
        .build<Long, Bucket>()

    /** 토큰 1개 = **LLM 요청 1회.** 한 번의 호출이 모델을 한 번 부를 때 쓴다. */
    fun checkRateLimit(userId: Long) = checkRateLimit(userId, 1)

    /**
     * 한 요청이 모델을 여러 번 부를 때, **부르는 횟수만큼** 토큰을 쓴다.
     *
     * 번역처럼 언어당 한 번씩 LLM 을 태우는 경로가 토큰 1 개만 쓰면, 8 개 언어를 요청해
     * 분당 80 회를 태울 수 있다. 같은 시간에 메타 생성 사용자는 10 회로 묶여 있다.
     * 토큰 수를 실제 호출 수에 맞춰야 이 제한이 의미를 갖는다.
     *
     * `tryConsume(n)` 은 **원자적**이다. 남은 토큰이 모자라면 하나도 쓰지 않고 거절한다.
     * `checkRateLimit` 을 n 번 반복하면 5 개만 소모하고 실패해 버킷만 비우게 된다.
     *
     * @param tokens 이 요청이 발생시킬 LLM 호출 수. 1 이상이어야 한다.
     */
    fun checkRateLimit(userId: Long, tokens: Int) {
        require(tokens >= 1) { "토큰 수는 1 이상이어야 합니다: $tokens" }
        val bucket = buckets.get(userId) { createBucket() }
        if (!bucket.tryConsume(tokens.toLong())) {
            throw AiRateLimitExceededException()
        }
    }

    private fun createBucket(): Bucket {
        val bandwidth = Bandwidth.builder()
            .capacity(MAX_REQUESTS_PER_MINUTE)
            .refillGreedy(MAX_REQUESTS_PER_MINUTE, Duration.ofMinutes(1))
            .build()
        return Bucket.builder().addLimit(bandwidth).build()
    }

    companion object {
        private const val MAX_REQUESTS_PER_MINUTE = 10L
    }
}

class AiRateLimitExceededException : BusinessException(
    "AI_RATE_LIMIT_EXCEEDED",
    "AI 요청 한도를 초과했습니다. 1분에 최대 10회까지 요청할 수 있습니다."
)
