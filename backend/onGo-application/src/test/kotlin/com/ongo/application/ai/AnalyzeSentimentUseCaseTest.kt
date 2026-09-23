package com.ongo.application.ai

import com.ongo.common.exception.BusinessException
import com.ongo.common.enums.AuthProvider
import com.ongo.common.enums.PlanType
import com.ongo.domain.user.User
import com.ongo.domain.user.UserRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnalyzeSentimentUseCaseTest {
    private val resolver = mockk<ChatClientResolver>()
    private val rateLimiter = mockk<AiRateLimiter>(relaxed = true)
    private val users = mockk<UserRepository>()
    private val usage = mockk<SentimentUsageRepository>()
    private val useCase = AnalyzeSentimentUseCase(resolver, rateLimiter, users, usage)

    @Test
    fun `provider failure is surfaced instead of being reported as neutral`() {
        every { users.findById(7L) } returns User(
            id = 7L, email = "test@example.com", name = "Test", provider = AuthProvider.GOOGLE,
            providerId = "test", planType = PlanType.STARTER,
        )
        every { usage.tryConsumeBatch(any(), any(), any()) } returns true
        every { resolver.resolve(7L) } throws IllegalStateException("AI key is missing")

        val error = assertFailsWith<BusinessException> {
            useCase.analyzeBatch(7L, listOf("댓글"))
        }

        assertEquals("AI_SENTIMENT_FAILED", error.code)
    }

    @Test
    fun `free plan is left unanalyzed without reserving daily usage`() {
        every { users.findById(7L) } returns User(
            id = 7L, email = "test@example.com", name = "Test", provider = AuthProvider.GOOGLE,
            providerId = "test", planType = PlanType.FREE,
        )

        val results = useCase.analyzeBatch(7L, listOf("댓글"))

        assertEquals(listOf(com.ongo.domain.comment.Comment.SENTIMENT_UNANALYZED), results)
        io.mockk.verify(exactly = 0) { usage.tryConsumeBatch(any(), any(), any()) }
        io.mockk.verify(exactly = 0) { resolver.resolve(any()) }
    }
}
