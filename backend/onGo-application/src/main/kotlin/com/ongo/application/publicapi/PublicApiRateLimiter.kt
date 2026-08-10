package com.ongo.application.publicapi

import com.github.benmanes.caffeine.cache.Caffeine
import com.ongo.common.exception.RateLimitExceededException
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Postiz-compatible protection for the expensive create-post endpoint.
 *
 * The bucket is scoped to the authenticated owner rather than an IP address,
 * so a shared NAT cannot make one creator block another. The durable
 * idempotency path still handles safe retries; this limiter only protects the
 * external-publish enqueue boundary.
 */
@Component
class PublicApiRateLimiter {
    private val buckets = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofHours(2))
        .maximumSize(10_000)
        .build<Long, Bucket>()

    fun checkCreatePost(userId: Long) {
        val bucket = buckets.get(userId) { createBucket() }
        if (!bucket.tryConsume(1)) {
            throw RateLimitExceededException(
                "공개 API 게시물 생성 한도를 초과했습니다. 한 시간에 최대 ${MAX_CREATE_POSTS}회까지 요청할 수 있습니다.",
            )
        }
    }

    private fun createBucket(): Bucket = Bucket.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(MAX_CREATE_POSTS)
                .refillGreedy(MAX_CREATE_POSTS, Duration.ofHours(1))
                .build(),
        )
        .build()

    companion object {
        const val MAX_CREATE_POSTS = 90L
    }
}
