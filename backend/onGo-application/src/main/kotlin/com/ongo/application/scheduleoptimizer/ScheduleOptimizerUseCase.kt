package com.ongo.application.scheduleoptimizer

import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.PromptTemplates
import com.ongo.application.ai.result.ScheduleOptimalResult
import com.ongo.application.credit.CreditService
import com.ongo.application.scheduleoptimizer.dto.*
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.scheduleoptimizer.OptimalSlot
import com.ongo.domain.scheduleoptimizer.OptimalSlotRepository
import com.ongo.domain.scheduleoptimizer.ScheduleRecommendation
import com.ongo.domain.scheduleoptimizer.ScheduleRecommendationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ScheduleOptimizerUseCase(
    private val slotRepository: OptimalSlotRepository,
    private val recRepository: ScheduleRecommendationRepository,
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
) {

    private val log = LoggerFactory.getLogger(ScheduleOptimizerUseCase::class.java)

    @Transactional
    fun generateOptimalSlots(userId: Long, platform: String): List<OptimalSlotResponse> {
        rateLimiter.checkRateLimit(userId)
        creditService.validateAndDeduct(userId, AiFeature.SCHEDULE_SUGGESTION)

        val userPrompt = PromptTemplates.SCHEDULE_SUGGESTION_USER
            .replace("{channelId}", "자동 분석")
            .replace("{platform}", platform)
            .replace("{category}", "자동 분석")
            .replace("{analyticsData}", "자동 분석")

        try {
            val result = chatClientResolver.resolve(userId).prompt()
                .system(PromptTemplates.SCHEDULE_SUGGESTION_SYSTEM)
                .user(userPrompt)
                .call()
                .entity(ScheduleOptimalResult::class.java)
                ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")

            // 기존 슬롯 삭제 후 신규 저장
            slotRepository.deleteByUserIdAndPlatform(userId, platform)

            val slots = result.slots.map { item ->
                OptimalSlot(
                    userId = userId,
                    platform = platform,
                    dayOfWeek = item.dayOfWeek,
                    hour = item.hour,
                    score = item.score,
                    audienceOnline = item.audienceOnline,
                    competitionLevel = item.competitionLevel,
                    reason = item.reason,
                )
            }

            val saved = slotRepository.saveBatch(slots)
            return saved.map { it.toResponse() }
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("AI 최적 업로드 시간 생성 실패, 크레딧 환불 처리: userId={}, platform={}", userId, platform, e)
            creditService.refundCredit(userId, AiFeature.SCHEDULE_SUGGESTION.creditCost, AiFeature.SCHEDULE_SUGGESTION.name)
            throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
        }
    }

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
