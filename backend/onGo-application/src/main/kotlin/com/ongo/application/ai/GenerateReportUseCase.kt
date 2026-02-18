package com.ongo.application.ai

import com.ongo.application.ai.result.PerformanceReportResult
import com.ongo.application.credit.CreditService
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

    fun execute(userId: Long, days: Int): PerformanceReportResult {
        rateLimiter.checkRateLimit(userId)
        creditService.validateAndDeduct(userId, AiFeature.PERFORMANCE_REPORT)

        val kpi = analyticsRepository.getDashboardKpi(userId, days)
        val topVideos = analyticsRepository.getTopVideos(userId, days, 5)
        val topVideosStr = topVideos.mapIndexed { i, v ->
            "${i + 1}. ${v.title}"
        }.joinToString("\n").ifEmpty { "데이터 없음" }

        val subscriberChange = kpi.totalSubscribersChange

        val userPrompt = PromptTemplates.PERFORMANCE_REPORT_USER
            .replace("{days}", days.toString())
            .replace("{totalViews}", kpi.totalViews.toString())
            .replace("{viewsChange}", String.format("%.1f", kpi.totalViewsChange))
            .replace("{totalLikes}", kpi.totalLikes.toString())
            .replace("{likesChange}", String.format("%.1f", kpi.totalLikesChange))
            .replace("{totalComments}", "N/A")
            .replace("{subscriberChange}", subscriberChange.toString())
            .replace("{topVideos}", topVideosStr)

        try {
            val result = chatClientResolver.resolve(userId).prompt()
                .system(PromptTemplates.PERFORMANCE_REPORT_SYSTEM)
                .user(userPrompt)
                .call()
                .entity(PerformanceReportResult::class.java)

            return result
                ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("AI 성과 리포트 생성 실패, 크레딧 환불 처리: userId={}", userId, e)
            creditService.refundCredit(userId, AiFeature.PERFORMANCE_REPORT.creditCost, AiFeature.PERFORMANCE_REPORT.name)
            throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
        }
    }
}
