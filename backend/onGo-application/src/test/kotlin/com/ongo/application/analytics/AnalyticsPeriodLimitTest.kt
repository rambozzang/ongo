package com.ongo.application.analytics

import com.ongo.common.enums.AuthProvider
import com.ongo.common.enums.PlanType
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.credit.CreditRepository
import com.ongo.domain.user.User
import com.ongo.domain.user.UserRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnalyticsPeriodLimitTest {
    private val analytics = mockk<AnalyticsRepository>(relaxed = true)
    private val users = mockk<UserRepository>()
    private val videos = mockk<VideoRepository>(relaxed = true)
    private val uploads = mockk<VideoUploadRepository>(relaxed = true)
    private val credits = mockk<CreditRepository>(relaxed = true)
    private val useCase = AnalyticsUseCase(analytics, users, videos, uploads, credits)

    @Test
    fun `period metadata clamps requests to the current plan and leaves Business unlimited`() {
        every { users.findById(1L) } returns user(PlanType.FREE)
        every { users.findById(2L) } returns user(PlanType.BUSINESS)
        every { users.findById(3L) } returns user(PlanType.STARTER)
        every { users.findById(4L) } returns user(PlanType.PRO)

        val free = useCase.limitPeriod(1L, 365)
        assertEquals(365, free.requestedDays)
        assertEquals(7, free.appliedDays)
        assertEquals(7, free.maxDays)
        assertTrue(free.wasTruncated)

        assertEquals(30, useCase.limitPeriod(3L, 365).appliedDays)
        assertEquals(365, useCase.limitPeriod(4L, 400).appliedDays)

        val business = useCase.limitPeriod(2L, 365)
        assertEquals(365, business.appliedDays)
        assertFalse(business.wasTruncated)
    }

    @Test
    fun `non-positive requested ranges normalize to one day`() {
        every { users.findById(1L) } returns user(PlanType.STARTER)
        val limit = useCase.limitPeriod(1L, 0)
        assertEquals(0, limit.requestedDays)
        assertEquals(1, limit.appliedDays)
        assertFalse(limit.wasTruncated)
    }

    @Test
    fun `heatmap forwards the already limited period to its repository query`() {
        every { analytics.getHeatmapData(1L, 7) } returns emptyMap()

        useCase.getHeatmap(1L, 7)

        verify(exactly = 1) { analytics.getHeatmapData(1L, 7) }
    }

    private fun user(plan: PlanType) = User(
        id = if (plan == PlanType.FREE) 1L else 2L,
        email = "creator@example.com",
        name = "Creator",
        provider = AuthProvider.GOOGLE,
        providerId = "google",
        planType = plan,
    )
}
