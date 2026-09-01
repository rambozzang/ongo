package com.ongo.application.ai

import com.ongo.application.credit.CreditService
import com.ongo.common.enums.PlanType
import com.ongo.common.exception.ForbiddenException
import com.ongo.domain.ai.WeeklyDigest
import com.ongo.domain.ai.WeeklyDigestRepository
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.accountdeletion.UserWriteGuard
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WeeklyDigestEntitlementTest {
    private val chatClientResolver = mockk<ChatClientResolver>()
    private val creditService = mockk<CreditService>()
    private val rateLimiter = mockk<AiRateLimiter>(relaxed = true)
    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val weeklyDigestRepository = mockk<WeeklyDigestRepository>()
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val userWriteGuard = mockk<UserWriteGuard>(relaxed = true)

    private val useCase = WeeklyDigestUseCase(
        chatClientResolver = chatClientResolver,
        creditService = creditService,
        rateLimiter = rateLimiter,
        analyticsRepository = analyticsRepository,
        weeklyDigestRepository = weeklyDigestRepository,
        subscriptionRepository = subscriptionRepository,
        userWriteGuard = userWriteGuard,
    )

    private val userId = 42L
    private val savedDigest = WeeklyDigest(
        id = 7L,
        userId = userId,
        weekStartDate = LocalDate.of(2026, 8, 17),
        weekEndDate = LocalDate.of(2026, 8, 23),
        summary = "실제 유료 다이제스트",
        topVideos = "상위 영상",
        anomalies = "변화",
        actionItems = "다음 행동",
    )

    private fun useSubscription(planType: PlanType) {
        every { subscriptionRepository.findByUserId(userId) } returns Subscription(
            userId = userId,
            planType = planType,
        )
    }

    @Test
    fun `무료 사용자는 기존에 생성된 다이제스트도 읽을 수 없다`() {
        useSubscription(PlanType.FREE)

        assertFailsWith<ForbiddenException> { useCase.getLatestDigest(userId) }

        verify(exactly = 0) { weeklyDigestRepository.findLatestByUserId(any()) }
    }

    @Test
    fun `Pro 사용자는 최신 다이제스트를 읽을 수 있다`() {
        useSubscription(PlanType.PRO)
        every { weeklyDigestRepository.findLatestByUserId(userId) } returns savedDigest

        val result = useCase.getLatestDigest(userId)

        assertEquals(savedDigest.summary, result.summary)
        verify(exactly = 1) { weeklyDigestRepository.findLatestByUserId(userId) }
    }

    @Test
    fun `Starter 사용자는 다이제스트 이력도 읽을 수 없다`() {
        useSubscription(PlanType.STARTER)

        assertFailsWith<ForbiddenException> { useCase.listDigests(userId, page = 0, size = 10) }

        verify(exactly = 0) { weeklyDigestRepository.findByUserId(any(), any(), any()) }
    }
}
