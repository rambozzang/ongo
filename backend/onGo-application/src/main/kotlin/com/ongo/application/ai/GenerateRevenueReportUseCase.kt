package com.ongo.application.ai

import com.ongo.application.ai.result.RevenueReportResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.application.revenue.RevenueAvailability
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.revenue.RevenueRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class GenerateRevenueReportUseCase(
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
    private val revenueRepository: RevenueRepository,
    private val analyticsRepository: AnalyticsRepository,
) {

    private val log = LoggerFactory.getLogger(GenerateRevenueReportUseCase::class.java)

    /**
     * 차감·환불은 [CreditService.withCredits] 한 곳에서 처리한다. `AI_PARSE_ERROR` 도
     * 환불 대상이다.
     */
    fun execute(userId: Long, days: Int): RevenueReportResult {
        rateLimiter.checkRateLimit(userId)

        val to = LocalDate.now()
        val from = to.minusDays(days.toLong())
        val formatter = DateTimeFormatter.ofPattern("MM/dd")

        val totalRevenue = revenueRepository.getTotalRevenue(userId, from, to)
        val dailyAverage = if (days > 0) totalRevenue / days else 0L

        // 실제로 측정된 수익이 없으면 유료 AI 를 부르지 않는다. `revenue_micro` 는
        // 기본값이 0 이라 "측정된 0" 처럼 보이지만, 모델에게 설명시킬 근거가 아니다.
        // 왜 없는지(재연동 필요/집계 대기/미지원)는 사유 문구가 구분해 알려 준다.
        val availability = RevenueAvailability.evaluate(
            revenueRepository.getRevenueStatusCounts(userId, from, to),
        )
        if (!availability.available) {
            throw BusinessException(
                "REVENUE_DATA_UNAVAILABLE",
                availability.reason ?: "광고 수익 데이터가 없어 수익 리포트를 생성할 수 없습니다.",
            )
        }

        val platformRevenue = revenueRepository.getPlatformRevenue(userId, from, to)
        val platformRevenueStr = platformRevenue.joinToString("\n") { pr ->
            "- ${pr.platform}: ${formatRevenue(pr.totalRevenueMicro)}원"
        }.ifEmpty { "플랫폼별 수익 데이터 없음" }

        val dailyRevenue = revenueRepository.getDailyRevenue(userId, from, to)
        val dailyTrendStr = dailyRevenue.takeLast(14).joinToString("\n") { dr ->
            "- ${dr.date.format(formatter)}: ${formatRevenue(dr.revenueMicro)}원"
        }.ifEmpty { "일별 수익 데이터 없음" }

        val userPrompt = PromptTemplates.REVENUE_REPORT_USER
            .replace("{days}", days.toString())
            .replace("{totalRevenue}", formatRevenue(totalRevenue))
            .replace("{dailyAverage}", formatRevenue(dailyAverage))
            .replace("{platformRevenue}", platformRevenueStr)
            .replace("{dailyTrend}", dailyTrendStr)

        return creditService.withCredits(userId, AiFeature.REVENUE_REPORT) {
            try {
                chatClientResolver.resolve(userId).prompt()
                    .system(PromptTemplates.REVENUE_REPORT_SYSTEM)
                    .user(userPrompt)
                    .call()
                    .entity(RevenueReportResult::class.java)
                    ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
            } catch (e: BusinessException) {
                throw e
            } catch (e: Exception) {
                log.error("AI 수익 리포트 생성 실패: userId={}", userId, e)
                throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
            }
        }
    }

    private fun formatRevenue(microAmount: Long): String {
        val amount = microAmount / 1_000_000.0
        return String.format("%,.0f", amount)
    }
}
