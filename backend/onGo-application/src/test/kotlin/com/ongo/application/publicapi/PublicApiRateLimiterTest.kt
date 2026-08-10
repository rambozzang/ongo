package com.ongo.application.publicapi

import com.ongo.common.exception.RateLimitExceededException
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class PublicApiRateLimiterTest {
    @Test
    fun `게시물 생성은 사용자별 시간당 90회에서 차단된다`() {
        val limiter = PublicApiRateLimiter()

        repeat(PublicApiRateLimiter.MAX_CREATE_POSTS.toInt()) {
            limiter.checkCreatePost(userId = 42L)
        }

        assertFailsWith<RateLimitExceededException> {
            limiter.checkCreatePost(userId = 42L)
        }
    }

    @Test
    fun `사용자별 버킷은 서로 격리된다`() {
        val limiter = PublicApiRateLimiter()

        repeat(PublicApiRateLimiter.MAX_CREATE_POSTS.toInt()) {
            limiter.checkCreatePost(userId = 42L)
        }

        limiter.checkCreatePost(userId = 43L)
    }
}
