package com.ongo.application.scheduleoptimizer

import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.PromptTemplates
import com.ongo.application.ai.result.ScheduleOptimalResult
import com.ongo.application.credit.CreditService
import com.ongo.application.schedule.ScheduleUseCase
import com.ongo.application.schedule.dto.PlatformScheduleConfig
import com.ongo.application.schedule.dto.UpdateScheduleRequest
import com.ongo.application.scheduleoptimizer.dto.*
import com.ongo.common.enums.AiFeature
import com.ongo.common.enums.Platform
import com.ongo.common.enums.ScheduleStatus
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.schedule.ScheduleRepository
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
    private val scheduleRepository: ScheduleRepository,
    private val scheduleUseCase: ScheduleUseCase,
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
        val rec = recRepository.findByIdAndUserId(id, userId)
            ?: throw NotFoundException("일정 추천", id)

        // Update the durable schedule and video_uploads queue before reporting
        // success. Marking only this row as APPLIED leaves the dispatcher at the
        // old time and makes the UI lie about the user's actual schedule.
        if (rec.status == "APPLIED") return rec.toRecResponse()
        if (rec.status != "PENDING") {
            throw BusinessException("SCHEDULE_RECOMMENDATION_NOT_APPLICABLE", "이미 처리된 일정 추천입니다")
        }

        val schedule = scheduleRepository.findByUserId(userId)
            .firstOrNull { it.videoId == rec.videoId && it.status == ScheduleStatus.SCHEDULED }
            ?: throw BusinessException(
                "SCHEDULE_RECOMMENDATION_NOT_APPLICABLE",
                "현재 예약에 연결되지 않은 일정 추천입니다. 예약을 새로 확인해주세요",
            )
        val recommendedPlatform = runCatching { Platform.valueOf(rec.platform.trim().uppercase()) }
            .getOrElse {
                throw BusinessException("SCHEDULE_RECOMMENDATION_NOT_APPLICABLE", "지원하지 않는 플랫폼의 일정 추천입니다")
            }

        val platformConfigs = schedule.platforms.map { (key, raw) ->
            val (platform, channelId) = parseScheduleKey(key)
                ?: throw BusinessException("SCHEDULE_RECOMMENDATION_NOT_APPLICABLE", "예약 플랫폼 정보가 올바르지 않습니다")
            val currentTime = platformTime(raw) ?: schedule.scheduledAt
            PlatformScheduleConfig(
                platform = platform,
                channelId = channelId,
                scheduledAt = if (platform == recommendedPlatform) rec.recommendedSchedule else currentTime,
            )
        }
        if (platformConfigs.none { it.platform == recommendedPlatform }) {
            throw BusinessException("SCHEDULE_RECOMMENDATION_NOT_APPLICABLE", "추천 플랫폼이 현재 예약 대상에 없습니다")
        }

        scheduleUseCase.updateSchedule(
            userId = userId,
            scheduleId = schedule.id ?: throw NotFoundException("예약", 0),
            request = UpdateScheduleRequest(
                scheduledAt = platformConfigs.minOf { it.scheduledAt ?: schedule.scheduledAt },
                platforms = platformConfigs,
            ),
        )
        if (!recRepository.updateStatus(id, userId, "APPLIED")) {
            throw NotFoundException("일정 추천", id)
        }
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

    private fun parseScheduleKey(key: String): Pair<Platform, Long?>? {
        val parts = key.split('#', limit = 2)
        val platform = runCatching { Platform.valueOf(parts[0].trim().uppercase()) }.getOrNull() ?: return null
        val channelId = parts.getOrNull(1)?.let { runCatching { it.toLong() }.getOrNull() }
            ?: if (parts.size == 1) null else return null
        return platform to channelId
    }

    private fun platformTime(raw: Any?): java.time.LocalDateTime? {
        val value = (raw as? Map<*, *>)?.get("scheduledAt")?.toString() ?: return null
        return runCatching { java.time.LocalDateTime.parse(value) }.getOrNull()
    }
}
