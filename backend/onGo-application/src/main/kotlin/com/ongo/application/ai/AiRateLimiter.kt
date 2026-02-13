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

    fun checkRateLimit(userId: Long) {
        val bucket = buckets.get(userId) { createBucket() }
        if (!bucket.tryConsume(1)) {
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
