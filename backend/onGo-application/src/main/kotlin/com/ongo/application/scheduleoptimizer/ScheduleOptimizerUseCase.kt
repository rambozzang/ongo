package com.ongo.application.scheduleoptimizer

import com.ongo.application.scheduleoptimizer.dto.*
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.scheduleoptimizer.OptimalSlot
import com.ongo.domain.scheduleoptimizer.OptimalSlotRepository
import com.ongo.domain.scheduleoptimizer.ScheduleRecommendation
import com.ongo.domain.scheduleoptimizer.ScheduleRecommendationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ScheduleOptimizerUseCase(
    private val slotRepository: OptimalSlotRepository,
    private val recRepository: ScheduleRecommendationRepository,
) {

    fun getSlots(userId: Long, platform: String): List<OptimalSlotResponse> {
        return slotRepository.findByPlatform(userId, platform).map { it.toResponse() }
    }

    fun getRecommendations(userId: Long): List<ScheduleRecommendationResponse> {
        return recRepository.findByUserId(userId).map { it.toRecResponse() }
    }

    @Transactional
    fun applyRecommendation(userId: Long, id: Long): ScheduleRecommendationResponse {
        val rec = recRepository.findById(id)
            ?: throw NotFoundException("일정 추천", id)
        recRepository.updateStatus(id, "APPLIED")
        return rec.copy(status = "APPLIED").toRecResponse()
    }

    fun getSummary(userId: Long): ScheduleOptimizerSummaryResponse {
        val recs = recRepository.findByUserId(userId)
        val applied = recs.count { it.status == "APPLIED" }
        val avgImprovement = if (recs.isNotEmpty()) recs.map { it.expectedImprovement }.average().toInt() else 0
        val slots = slotRepository.findByUserId(userId)
        val best = slots.maxByOrNull { it.score }
        return ScheduleOptimizerSummaryResponse(
            totalRecommendations = recs.size,
            appliedCount = applied,
            avgImprovement = avgImprovement,
            bestDay = best?.dayOfWeek ?: "",
            bestHour = best?.hour ?: 0,
        )
    }

    private fun OptimalSlot.toResponse() = OptimalSlotResponse(
        id = id!!, platform = platform, dayOfWeek = dayOfWeek, hour = hour,
        score = score, audienceOnline = audienceOnline, competitionLevel = competitionLevel,
        reason = reason, createdAt = createdAt,
    )

    private fun ScheduleRecommendation.toRecResponse() = ScheduleRecommendationResponse(
        id = id!!, videoId = videoId, videoTitle = videoTitle,
        currentSchedule = currentSchedule, recommendedSchedule = recommendedSchedule,
        platform = platform, expectedImprovement = expectedImprovement,
        confidence = confidence, status = status, createdAt = createdAt,
    )
}
