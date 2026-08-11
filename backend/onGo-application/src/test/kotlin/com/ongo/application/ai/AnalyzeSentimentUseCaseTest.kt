package com.ongo.application.ai

import com.ongo.common.exception.BusinessException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnalyzeSentimentUseCaseTest {
    private val resolver = mockk<ChatClientResolver>()
    private val rateLimiter = mockk<AiRateLimiter>(relaxed = true)
    private val useCase = AnalyzeSentimentUseCase(resolver, rateLimiter)

    @Test
    fun `provider failure is surfaced instead of being reported as neutral`() {
        every { resolver.resolve(7L) } throws IllegalStateException("AI key is missing")

        val error = assertFailsWith<BusinessException> {
            useCase.analyzeBatch(7L, listOf("댓글"))
        }

        assertEquals("AI_SENTIMENT_FAILED", error.code)
    }
}
