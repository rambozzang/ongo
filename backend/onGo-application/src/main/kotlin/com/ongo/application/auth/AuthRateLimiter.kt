package com.ongo.application.auth

import com.github.benmanes.caffeine.cache.Caffeine
import com.ongo.common.exception.BusinessException
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class AuthRateLimiter {

    private val loginBuckets = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(30))
        .maximumSize(50_000)
        .build<String, Bucket>()

    private val refreshBuckets = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(10))
        .maximumSize(50_000)
        .build<String, Bucket>()

    fun checkLoginRateLimit(clientIp: String) {
        val bucket = loginBuckets.get(clientIp) { createLoginBucket() }
        if (!bucket.tryConsume(1)) {
            throw AuthRateLimitExceededException("로그인 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.")
        }
    }

    fun checkRefreshRateLimit(clientIp: String) {
        val bucket = refreshBuckets.get(clientIp) { createRefreshBucket() }
        if (!bucket.tryConsume(1)) {
            throw AuthRateLimitExceededException("토큰 갱신 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.")
        }
    }

    private fun createLoginBucket(): Bucket {
        val bandwidth = Bandwidth.builder()
            .capacity(5)
            .refillGreedy(5, Duration.ofMinutes(15))
            .build()
        return Bucket.builder().addLimit(bandwidth).build()
    }

    private fun createRefreshBucket(): Bucket {
        val bandwidth = Bandwidth.builder()
            .capacity(10)
            .refillGreedy(10, Duration.ofMinutes(1))
            .build()
        return Bucket.builder().addLimit(bandwidth).build()
    }
}

class AuthRateLimitExceededException(message: String) : BusinessException(
    "AUTH_RATE_LIMIT_EXCEEDED", message
)
