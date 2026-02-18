package com.ongo.application.ai

import com.ongo.application.ai.result.ScheduleSuggestionResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.channel.ChannelRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SuggestScheduleUseCase(
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
    private val channelRepository: ChannelRepository,
    private val analyticsRepository: AnalyticsRepository,
) {

    private val log = LoggerFactory.getLogger(SuggestScheduleUseCase::class.java)

    fun executeInternal(userId: Long, channelId: Long): ScheduleSuggestionResult {
        val channel = channelRepository.findById(channelId)
            ?: throw NotFoundException("채널", channelId)

        if (channel.userId != userId) {
            throw ForbiddenException("해당 채널에 접근 권한이 없습니다")
        }

        val heatmapData = analyticsRepository.getHeatmapData(userId)
        val analyticsDataStr = heatmapData.entries.joinToString("\n") { (day, hours) ->
            "$day: ${hours.entries.sortedByDescending { it.value }.take(5).joinToString(", ") { "${it.key}시=${it.value}조회" }}"
        }.ifEmpty { "아직 충분한 분석 데이터가 없습니다." }

        val userPrompt = PromptTemplates.SCHEDULE_SUGGESTION_USER
            .replace("{channelId}", channelId.toString())
            .replace("{platform}", channel.platform.name)
            .replace("{category}", "일반")
            .replace("{analyticsData}", analyticsDataStr)

        val result = chatClientResolver.resolve(userId).prompt()
            .system(PromptTemplates.SCHEDULE_SUGGESTION_SYSTEM)
            .user(userPrompt)
            .call()
            .entity(ScheduleSuggestionResult::class.java)

        return result
            ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
    }

    fun execute(userId: Long, channelId: Long): ScheduleSuggestionResult {
        rateLimiter.checkRateLimit(userId)

        val channel = channelRepository.findById(channelId)
            ?: throw NotFoundException("채널", channelId)

        if (channel.userId != userId) {
            throw ForbiddenException("해당 채널에 접근 권한이 없습니다")
        }

        creditService.validateAndDeduct(userId, AiFeature.SCHEDULE_SUGGESTION)

        val heatmapData = analyticsRepository.getHeatmapData(userId)
        val analyticsDataStr = heatmapData.entries.joinToString("\n") { (day, hours) ->
            "$day: ${hours.entries.sortedByDescending { it.value }.take(5).joinToString(", ") { "${it.key}시=${it.value}조회" }}"
        }.ifEmpty { "아직 충분한 분석 데이터가 없습니다." }

        val userPrompt = PromptTemplates.SCHEDULE_SUGGESTION_USER
            .replace("{channelId}", channelId.toString())
            .replace("{platform}", channel.platform.name)
            .replace("{category}", "일반")
            .replace("{analyticsData}", analyticsDataStr)

        try {
            val result = chatClientResolver.resolve(userId).prompt()
                .system(PromptTemplates.SCHEDULE_SUGGESTION_SYSTEM)
                .user(userPrompt)
                .call()
                .entity(ScheduleSuggestionResult::class.java)

            return result
                ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("AI 스케줄 추천 실패, 크레딧 환불 처리: userId={}", userId, e)
            creditService.refundCredit(userId, AiFeature.SCHEDULE_SUGGESTION.creditCost, AiFeature.SCHEDULE_SUGGESTION.name)
            throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
        }
    }
}
