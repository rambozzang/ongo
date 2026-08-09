package com.ongo.application.scheduleoptimizer

import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.analytics.AnalyticsUseCase
import com.ongo.application.credit.CreditService
import com.ongo.application.schedule.ScheduleUseCase
import com.ongo.application.analytics.dto.OptimalTimeSlot
import com.ongo.application.analytics.dto.OptimalTimesResponse
import com.ongo.common.enums.ScheduleStatus
import com.ongo.common.enums.Platform
import com.ongo.domain.schedule.Schedule
import com.ongo.domain.schedule.ScheduleRepository
import com.ongo.domain.scheduleoptimizer.OptimalSlotRepository
import com.ongo.domain.scheduleoptimizer.ScheduleRecommendation
import com.ongo.domain.scheduleoptimizer.ScheduleRecommendationRepository
import com.ongo.domain.video.VideoRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScheduleOptimizerUseCaseTest {

    private val recommendations = InMemoryRecommendationRepository()
    private val scheduleRepository = mockk<ScheduleRepository>()
    private val scheduleUseCase = mockk<ScheduleUseCase>(relaxed = true)
    private val analyticsUseCase = mockk<AnalyticsUseCase>()
    private val videoRepository = mockk<VideoRepository>()
    private val useCase = ScheduleOptimizerUseCase(
        slotRepository = mockk<OptimalSlotRepository>(),
        recRepository = recommendations,
        chatClientResolver = mockk<ChatClientResolver>(),
        creditService = mockk<CreditService>(),
        rateLimiter = mockk<AiRateLimiter>(),
        scheduleRepository = scheduleRepository,
        scheduleUseCase = scheduleUseCase,
        analyticsUseCase = analyticsUseCase,
        videoRepository = videoRepository,
    )

    init {
        every { scheduleRepository.findByUserId(7L) } returns listOf(
            Schedule(
                id = 99L,
                videoId = 42L,
                userId = 7L,
                scheduledAt = LocalDateTime.of(2026, 8, 10, 8, 0),
                status = ScheduleStatus.SCHEDULED,
                platforms = mapOf(
                    "YOUTUBE#101" to mapOf("scheduledAt" to "2026-08-10T08:00:00"),
                    "YOUTUBE#303" to mapOf("scheduledAt" to "2026-08-10T08:15:00"),
                    "TIKTOK#202" to mapOf("scheduledAt" to "2026-08-10T08:30:00"),
                ),
            ),
        )
        every { scheduleRepository.findByUserId(8L) } returns emptyList()
        every { videoRepository.findById(42L) } returns null
    }

    @Test
    fun `apply recommendation only changes a recommendation owned by the current user`() {
        val recommendation = recommendations.save(recommendation(userId = 7L))
        val recommendationId = recommendation.id!!

        val applied = useCase.applyRecommendation(userId = 7L, id = recommendationId)

        assertEquals("APPLIED", applied.status)
        assertEquals("APPLIED", recommendations.findByIdAndUserId(recommendationId, 7L)?.status)
        verify {
            scheduleUseCase.updateSchedule(
                userId = 7L,
                scheduleId = 99L,
                request = match { request ->
                    request.platforms.orEmpty().first { it.channelId == 101L }.scheduledAt == LocalDateTime.of(2026, 8, 10, 9, 0) &&
                        request.platforms.orEmpty().first { it.channelId == 303L }.scheduledAt == LocalDateTime.of(2026, 8, 10, 8, 15)
                },
            )
        }
    }

    @Test
    fun `apply recommendation hides another user's recommendation`() {
        val recommendation = recommendations.save(recommendation(userId = 7L))
        val recommendationId = recommendation.id!!

        assertFailsWith<RuntimeException> {
            useCase.applyRecommendation(userId = 8L, id = recommendationId)
        }
        assertEquals("PENDING", recommendations.findByIdAndUserId(recommendationId, 7L)?.status)
    }

    @Test
    fun `legacy provider-wide recommendation is rejected for multiple accounts`() {
        val recommendation = recommendations.save(recommendation(userId = 7L).copy(channelId = null))

        assertFailsWith<RuntimeException> {
            useCase.applyRecommendation(userId = 7L, id = recommendation.id!!)
        }
        assertEquals("PENDING", recommendations.findByIdAndUserId(recommendation.id!!, 7L)?.status)
        verify(exactly = 0) { scheduleUseCase.updateSchedule(any(), any(), any()) }
    }

    @Test
    fun `recommendation listing materializes an analytics backed next slot`() {
        val tomorrow = LocalDateTime.now(ScheduleUseCase.KST).toLocalDate().plusDays(1)
        every { analyticsUseCase.getOptimalPublishTimes(7L, Platform.YOUTUBE) } returns OptimalTimesResponse(
            slots = listOf(
                OptimalTimeSlot(
                    dayOfWeek = tomorrow.dayOfWeek.value % 7,
                    dayLabel = "내일",
                    hour = 9,
                    timeLabel = "09:00",
                    expectedViews = 100,
                    engagementRate = 4.2,
                    confidenceScore = 76.0,
                    score = 90.0,
                ),
            ),
        )

        val result = useCase.getRecommendations(userId = 7L)

        assertEquals(2, result.size)
        assertEquals(setOf(101L, 303L), result.map { it.channelId }.toSet())
        assertEquals(setOf("YOUTUBE"), result.map { it.platform }.toSet())
        assertEquals(setOf("PENDING"), result.map { it.status }.toSet())
        assertEquals(setOf(76), result.map { it.confidence }.toSet())
    }

    private fun recommendation(userId: Long) = ScheduleRecommendation(
        userId = userId,
        videoId = 42L,
        channelId = 101L,
        videoTitle = "테스트 영상",
        recommendedSchedule = LocalDateTime.of(2026, 8, 10, 9, 0),
        platform = "YOUTUBE",
    )

    private class InMemoryRecommendationRepository : ScheduleRecommendationRepository {
        private var nextId = 1L
        private val records = linkedMapOf<Long, ScheduleRecommendation>()

        override fun findByIdAndUserId(id: Long, userId: Long) =
            records[id]?.takeIf { it.userId == userId }

        override fun findByUserId(userId: Long) = records.values.filter { it.userId == userId }

        override fun save(rec: ScheduleRecommendation) = rec.copy(id = nextId++).also {
            records[it.id!!] = it
        }

        override fun updateStatus(id: Long, userId: Long, status: String): Boolean {
            val current = findByIdAndUserId(id, userId) ?: return false
            records[id] = current.copy(status = status)
            return true
        }
    }
}
