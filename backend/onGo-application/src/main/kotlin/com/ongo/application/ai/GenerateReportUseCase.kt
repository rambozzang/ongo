package com.ongo.application.ai

import com.ongo.application.ai.result.PerformanceReportResult
import com.ongo.application.credit.CreditService
import com.ongo.domain.analytics.MetricChange
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.domain.analytics.AnalyticsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GenerateReportUseCase(
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
    private val analyticsRepository: AnalyticsRepository,
) {

    private val log = LoggerFactory.getLogger(GenerateReportUseCase::class.java)

    /**
     * 차감·환불은 [CreditService.withCredits] 한 곳에서 처리한다. `AI_PARSE_ERROR` 도
     * 환불 대상이다 — 예전에는 그 분기가 환불 없이 그대로 올라갔다.
     */
    fun execute(userId: Long, days: Int): PerformanceReportResult {
        rateLimiter.checkRateLimit(userId)

        val kpi = analyticsRepository.getDashboardKpi(userId, days)
        val topVideos = analyticsRepository.getTopVideos(userId, days, 5)
        val topVideosStr = topVideos.mapIndexed { i, v ->
            "${i + 1}. ${v.title}"
        }.joinToString("\n").ifEmpty { "데이터 없음" }

        val subscriberChange = kpi.totalSubscribersChange

        val userPrompt = PromptTemplates.PERFORMANCE_REPORT_USER
            .replace("{days}", days.toString())
            /*
             * **`Long?.toString()` 은 문자열 `"null"` 을 만든다.** 그 값이 프롬프트에
             * 들어가면 모델은 그것을 수치로 읽고 없는 성과를 설명한다.
             *
             * 세 지표는 수집하는 플랫폼이 없으면 `null` 이다(Tumblr 는 노트 총합을
             * `views` 에, Pinterest 는 저장 수를 `likes` 에 넣어 집계에서 빠진다).
             * **실측 0 은 그대로 `"0"`** 이므로 관측은 보존된다.
             */
            .replace("{totalViews}", MetricChange.describeCount(kpi.totalViews))
            // 비교 불가를 숫자로 채우면 모델이 없는 추세를 지어낸다. 단위는 값이 들고 온다.
            .replace("{viewsChange}", MetricChange.describePercent(kpi.totalViewsChange))
            .replace("{totalLikes}", MetricChange.describeCount(kpi.totalLikes))
            .replace("{likesChange}", MetricChange.describePercent(kpi.totalLikesChange))
            .replace("{totalComments}", MetricChange.describeCount(kpi.totalComments))
            .replace("{subscriberChange}", MetricChange.describeCount(subscriberChange))
            .replace("{topVideos}", topVideosStr)

        return creditService.withCredits(userId, AiFeature.PERFORMANCE_REPORT) {
            try {
                chatClientResolver.resolve(userId).prompt()
                    .system(PromptTemplates.PERFORMANCE_REPORT_SYSTEM)
                    .user(userPrompt)
                    .call()
                    .entity(PerformanceReportResult::class.java)
                    ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
            } catch (e: BusinessException) {
                throw e
            } catch (e: Exception) {
                log.error("AI 성과 리포트 생성 실패: userId={}", userId, e)
                throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
            }
        }
    }
}
