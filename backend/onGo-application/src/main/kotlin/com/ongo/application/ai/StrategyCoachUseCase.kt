package com.ongo.application.ai

import com.ongo.application.ai.result.StrategyCoachResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.competitor.CompetitorRepository
import com.ongo.domain.video.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class StrategyCoachUseCase(
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
    private val analyticsRepository: AnalyticsRepository,
    private val videoRepository: VideoRepository,
    private val competitorRepository: CompetitorRepository,
) {

    private val log = LoggerFactory.getLogger(StrategyCoachUseCase::class.java)

    fun execute(userId: Long, includeCompetitors: Boolean = true, focusArea: String? = null): StrategyCoachResult {
        rateLimiter.checkRateLimit(userId)
        creditService.validateAndDeduct(userId, AiFeature.STRATEGY_COACH)

        val kpi = analyticsRepository.getDashboardKpi(userId, 30)

        val videos = videoRepository.findByUserId(userId, page = 0, size = 50)
        val recentVideosStr = videos.take(15).mapIndexed { i, v ->
            "${i + 1}. ${v.title} (카테고리: ${v.category ?: "미분류"})"
        }.joinToString("\n").ifEmpty { "데이터 없음" }

        val competitorStr = if (includeCompetitors) {
            val competitors = competitorRepository.findByUserId(userId)
            competitors.joinToString("\n") { comp ->
                "- ${comp.channelName} (${comp.platform}, 구독자: ${comp.subscriberCount}, 평균 조회수: ${comp.avgViews})"
            }.ifEmpty { "경쟁자 데이터 없음" }
        } else {
            "경쟁자 분석 미포함"
        }

        val userPrompt = PromptTemplates.STRATEGY_COACH_USER
            .replace("{totalViews}", kpi.totalViews.toString())
            .replace("{viewsChange}", String.format("%.1f", kpi.totalViewsChange))
            .replace("{totalLikes}", kpi.totalLikes.toString())
            .replace("{likesChange}", String.format("%.1f", kpi.totalLikesChange))
            .replace("{subscriberChange}", kpi.totalSubscribersChange.toString())
            .replace("{recentVideos}", InputSanitizer.sanitize(recentVideosStr))
            .replace("{competitorData}", InputSanitizer.sanitize(competitorStr))
            .replace("{focusArea}", InputSanitizer.sanitize(focusArea ?: "전체"))

        try {
            val result = chatClientResolver.resolve(userId).prompt()
                .system(PromptTemplates.STRATEGY_COACH_SYSTEM)
                .user(userPrompt)
                .call()
                .entity(StrategyCoachResult::class.java)

            return result
                ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("AI 전략 코치 분석 실패, 크레딧 환불 처리: userId={}", userId, e)
            creditService.refundCredit(userId, AiFeature.STRATEGY_COACH.creditCost, AiFeature.STRATEGY_COACH.name)
            throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
        }
    }
}
