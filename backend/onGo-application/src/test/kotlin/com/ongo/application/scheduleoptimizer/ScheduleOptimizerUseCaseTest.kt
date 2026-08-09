package com.ongo.application.scheduleoptimizer

import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.credit.CreditService
import com.ongo.application.schedule.ScheduleUseCase
import com.ongo.common.enums.ScheduleStatus
import com.ongo.domain.schedule.Schedule
import com.ongo.domain.schedule.ScheduleRepository
import com.ongo.domain.scheduleoptimizer.OptimalSlotRepository
import com.ongo.domain.scheduleoptimizer.ScheduleRecommendation
import com.ongo.domain.scheduleoptimizer.ScheduleRecommendationRepository
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
    private val useCase = ScheduleOptimizerUseCase(
        slotRepository = mockk<OptimalSlotRepository>(),
        recRepository = recommendations,
        chatClientResolver = mockk<ChatClientResolver>(),
        creditService = mockk<CreditService>(),
        rateLimiter = mockk<AiRateLimiter>(),
        scheduleRepository = scheduleRepository,
        scheduleUseCase = scheduleUseCase,
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
                    "TIKTOK#202" to mapOf("scheduledAt" to "2026-08-10T08:30:00"),
                ),
            ),
        )
        every { scheduleRepository.findByUserId(8L) } returns emptyList()
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
                request = any(),
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

    private fun recommendation(userId: Long) = ScheduleRecommendation(
        userId = userId,
        videoId = 42L,
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
