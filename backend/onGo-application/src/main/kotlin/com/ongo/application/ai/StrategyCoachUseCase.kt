package com.ongo.application.ai

import com.ongo.application.ai.result.StrategyCoachResult
import com.ongo.application.credit.CreditService
import com.ongo.domain.analytics.MetricChange
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

    /**
     * 차감·환불은 [CreditService.withCredits] 한 곳에서 처리한다.
     *
     * 예전에는 `catch (BusinessException) { throw e }` 가 환불 없이 그대로 올라가,
     * `AI_PARSE_ERROR` 로 끝난 호출은 결과 없이 크레딧만 사라졌다.
     */
    fun execute(userId: Long, includeCompetitors: Boolean = true, focusArea: String? = null): StrategyCoachResult {
        rateLimiter.checkRateLimit(userId)

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
            /*
             * **`Long?.toString()` 은 문자열 `"null"` 을 만든다.** 수집하는 플랫폼이 없으면
             * `null` 이므로 문장으로 바꾼다. **실측 0 은 `"0"`** 이라 관측은 남는다.
             */
            .replace("{totalViews}", MetricChange.describeCount(kpi.totalViews))
            // 비교 불가를 숫자로 채우면 모델이 없는 추세를 지어낸다. 단위는 값이 들고 온다.
            .replace("{viewsChange}", MetricChange.describePercent(kpi.totalViewsChange))
            .replace("{totalLikes}", MetricChange.describeCount(kpi.totalLikes))
            .replace("{likesChange}", MetricChange.describePercent(kpi.totalLikesChange))
            .replace("{subscriberChange}", MetricChange.describeCount(kpi.totalSubscribersChange))
            .replace("{recentVideos}", InputSanitizer.sanitize(recentVideosStr))
            .replace("{competitorData}", InputSanitizer.sanitize(competitorStr))
            .replace("{focusArea}", InputSanitizer.sanitize(focusArea ?: "전체"))

        return creditService.withCredits(userId, AiFeature.STRATEGY_COACH) {
            try {
                chatClientResolver.resolve(userId).prompt()
                    .system(PromptTemplates.STRATEGY_COACH_SYSTEM)
                    .user(userPrompt)
                    .call()
                    .entity(StrategyCoachResult::class.java)
                    ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
            } catch (e: BusinessException) {
                throw e
            } catch (e: Exception) {
                log.error("AI 전략 코치 분석 실패: userId={}", userId, e)
                throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
            }
        }
    }
}
