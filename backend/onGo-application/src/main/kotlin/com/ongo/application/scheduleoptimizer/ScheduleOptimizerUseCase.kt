package com.ongo.application.scheduleoptimizer

import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.PromptTemplates
import com.ongo.application.ai.result.ScheduleOptimalResult
import com.ongo.application.analytics.AnalyticsUseCase
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
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.schedule.ScheduleRepository
import com.ongo.domain.scheduleoptimizer.OptimalSlot
import com.ongo.domain.scheduleoptimizer.OptimalSlotRepository
import com.ongo.domain.scheduleoptimizer.ScheduleRecommendation
import com.ongo.domain.scheduleoptimizer.ScheduleRecommendationRepository
import com.ongo.domain.video.VideoRepository
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
    private val analyticsUseCase: AnalyticsUseCase,
    private val videoRepository: VideoRepository,
    private val channelRepository: ChannelRepository,
) {

    private val log = LoggerFactory.getLogger(ScheduleOptimizerUseCase::class.java)

    companion object {
        /** 프롬프트에 넣을 최적 시간 후보 상한. 상위 구간만으로 근거는 충분하다. */
        private const val ANALYTICS_SLOT_LIMIT = 12

        /**
         * 그 슬롯의 참여율을 잰 적이 없을 때 프롬프트에 넣는 문구.
         *
         * 숫자가 아니라 **문장**이어야 한다 — 어떤 숫자를 넣든 모델은 그것을 관측으로
         * 읽고 없는 근거로 일정을 추천한다. 단위(`%`)도 붙이지 않는다.
         */
        const val ENGAGEMENT_NOT_MEASURED = "측정 불가(수집하는 플랫폼 없음)"
    }

    /**
     * **트랜잭션을 열지 않는다.** LLM 호출을 `@Transactional` 안에 두면 `ai_credits` 행
     * 잠금과 DB 커넥션이 모델 응답 시간만큼 묶인다. 차감·환불의 커밋 경계는
     * [CreditService.withCredits] 가 잡고, 결과 저장은 호출 이후 짧게 끝난다.
     *
     * 프롬프트에는 실제 데이터만 넣는다. 예전에는 채널 ID·카테고리·성과 데이터 자리에
     * 전부 `"자동 분석"` 이라는 문자열을 넣고 정가로 과금했다 — 모델은 아무 근거 없이
     * 일반론을 지어냈고 사용자는 그걸 자기 채널 분석 결과로 읽었다.
     */
    fun generateOptimalSlots(userId: Long, platform: String): List<OptimalSlotResponse> {
        rateLimiter.checkRateLimit(userId)

        val targetPlatform = runCatching { Platform.valueOf(platform.trim().uppercase()) }
            .getOrElse {
                throw BusinessException("UNSUPPORTED_PLATFORM", "지원하지 않는 플랫폼입니다: $platform")
            }

        // 분석 집계(findDailyAnalyticsByChannelIds)가 해당 플랫폼 업로드 전체를 대상으로
        // 하므로 채널도 같은 범위로 모은다. 한 플랫폼에 여러 계정을 연동할 수 있다.
        val platformChannelIds = channelRepository.findByUserId(userId)
            .filter { it.platform == targetPlatform }
            .map { it.platformChannelId }
        if (platformChannelIds.isEmpty()) {
            throw BusinessException(
                "CHANNEL_NOT_CONNECTED",
                "$platform 채널이 연동되어 있지 않아 최적 업로드 시간을 분석할 수 없습니다",
            )
        }

        // 근거가 없으면 유료 AI 를 부르지 않는다. 차감 전에 거절해야 크레딧이 사라지지 않는다.
        val optimalTimes = analyticsUseCase.getOptimalPublishTimes(userId, targetPlatform)
        if (optimalTimes.slots.isEmpty()) {
            throw BusinessException(
                "ANALYTICS_DATA_UNAVAILABLE",
                "$platform 업로드 성과 데이터가 없어 최적 업로드 시간을 분석할 수 없습니다. " +
                    "게시 후 분석 데이터가 쌓이면 다시 시도해주세요",
            )
        }
        val analyticsData = optimalTimes.slots
            .sortedByDescending { it.score }
            .take(ANALYTICS_SLOT_LIMIT)
            .joinToString("\n") { slot ->
                /*
                 * **`String.format("%.2f", null)` 은 문자열 `"null"` 을 만든다.**
                 *
                 * 참여 지표를 하나도 보고하지 않는 플랫폼의 슬롯은 참여율이 `null` 이다.
                 * 예전에는 그 자리가 서버에서 `0.0` 으로 채워져 "참여율 0.00%" 가
                 * 프롬프트에 들어갔고, 모델은 그것을 관측으로 읽어 "참여가 없는 시간대"
                 * 라는 근거로 일정을 추천했다. 유료 호출이라 대가까지 치른다.
                 *
                 * 단위(`%`)는 값이 직접 들고 온다 — 밖에 붙이면 "측정 불가%" 가 된다.
                 */
                val engagement = slot.engagementRate
                    ?.let { String.format("%.2f%%", it) }
                    ?: ENGAGEMENT_NOT_MEASURED
                "- ${slot.dayLabel} ${slot.timeLabel}: 조회수 중앙값 ${slot.expectedViews}, " +
                    "참여율 $engagement, " +
                    "신뢰도 ${slot.confidenceScore.toInt()}%"
            }

        val userPrompt = PromptTemplates.SCHEDULE_SUGGESTION_USER
            .replace("{channelId}", platformChannelIds.joinToString(", "))
            .replace("{platform}", platform)
            // 채널 엔티티에 카테고리가 없다. 임의의 값으로 꾸미지 않고 부재를 명시한다.
            .replace("{category}", "미지정")
            .replace("{analyticsData}", analyticsData)

        return creditService.withCredits(userId, AiFeature.SCHEDULE_SUGGESTION) {
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
            saved.map { it.toResponse() }
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("AI 최적 업로드 시간 생성 실패: userId={}, platform={}", userId, platform, e)
            throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
        }
        }
    }

    fun getSlots(userId: Long, platform: String): List<OptimalSlotResponse> {
        return slotRepository.findByPlatform(userId, platform).map { it.toResponse() }
    }

    @Transactional
    fun getRecommendations(userId: Long): List<ScheduleRecommendationResponse> {
        createRecommendationsFromAnalytics(userId)
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

        val parsedTargets = schedule.platforms.map { (key, raw) ->
            val (platform, channelId) = parseScheduleKey(key)
                ?: throw BusinessException("SCHEDULE_RECOMMENDATION_NOT_APPLICABLE", "예약 플랫폼 정보가 올바르지 않습니다")
            Triple(platform, channelId, platformTime(raw) ?: schedule.scheduledAt)
        }
        val matchingTargetIndexes = parsedTargets.mapIndexedNotNull { index, (platform, channelId, _) ->
            if (platform == recommendedPlatform && (rec.channelId == null || channelId == rec.channelId)) index else null
        }
        if (rec.channelId == null && matchingTargetIndexes.size != 1) {
            throw BusinessException(
                "SCHEDULE_RECOMMENDATION_NOT_APPLICABLE",
                "기존 일정 추천에 채널 계정 정보가 없어 다중 계정 예약에 적용할 수 없습니다",
            )
        }
        val platformConfigs = parsedTargets.mapIndexed { index, (platform, channelId, currentTime) ->
            PlatformScheduleConfig(
                platform = platform,
                channelId = channelId,
                scheduledAt = if (index in matchingTargetIndexes) rec.recommendedSchedule else currentTime,
            )
        }
        if (matchingTargetIndexes.isEmpty()) {
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
        id = id!!, videoId = videoId, channelId = channelId, videoTitle = videoTitle,
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

    /**
     * Materialise recommendations from the same analytics data used by the
     * compose screen. Previously this table had a reader and an apply endpoint
     * but no producer, so the calendar could never show a recommendation.
     * Generation is idempotent for the same video/platform/current/next slot.
     */
    private fun createRecommendationsFromAnalytics(userId: Long) {
        val now = java.time.LocalDateTime.now(ScheduleUseCase.KST)
        val existing = recRepository.findByUserId(userId)
        scheduleRepository.findByUserId(userId)
            .filter { it.status == ScheduleStatus.SCHEDULED }
            .forEach { schedule ->
                val videoTitle = videoRepository.findById(schedule.videoId)?.title ?: "예약 영상"
                schedule.platforms.forEach platformTarget@ { (key, raw) ->
                    val (platform, channelId) = parseScheduleKey(key) ?: return@platformTarget
                    val current = platformTime(raw) ?: schedule.scheduledAt
                    val slot = runCatching {
                        analyticsUseCase.getOptimalPublishTimes(userId, platform).slots.firstOrNull()
                    }.getOrNull() ?: return@platformTarget
                    val recommended = nextSlotAfter(now, slot.dayOfWeek, slot.hour) ?: return@platformTarget
                    if (recommended == current) return@platformTarget
                    val duplicate = existing.any {
                        it.videoId == schedule.videoId &&
                            it.channelId == channelId &&
                            it.platform == platform.name &&
                            it.currentSchedule == current &&
                            it.recommendedSchedule == recommended &&
                            it.status == "PENDING"
                    }
                    if (!duplicate) {
                        recRepository.save(
                            ScheduleRecommendation(
                                userId = userId,
                                videoId = schedule.videoId,
                                channelId = channelId,
                                videoTitle = videoTitle,
                                currentSchedule = current,
                                recommendedSchedule = recommended,
                                platform = platform.name,
                                // This value is a confidence signal, not a
                                // fabricated view forecast. The UI displays
                                // the confidence field explicitly.
                                expectedImprovement = 0,
                                confidence = slot.confidenceScore.toInt().coerceIn(0, 100),
                            ),
                        )
                    }
                }
            }
    }

    private fun nextSlotAfter(
        now: java.time.LocalDateTime,
        dayOfWeek: Int,
        hour: Int,
    ): java.time.LocalDateTime? {
        val safeHour = hour.coerceIn(0, 23)
        return (0L..14L)
            .map { now.toLocalDate().plusDays(it).atTime(safeHour, 0) }
            .firstOrNull { candidate ->
                candidate.isAfter(now.plusMinutes(5)) && candidate.dayOfWeek.value % 7 == dayOfWeek
            }
    }
}
