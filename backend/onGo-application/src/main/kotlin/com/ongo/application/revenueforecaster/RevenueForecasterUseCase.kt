package com.ongo.application.revenueforecaster

import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.PromptTemplates
import com.ongo.application.ai.result.RevenueForecastResult
import com.ongo.application.credit.CreditService
import com.ongo.application.revenueforecaster.dto.*
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.domain.revenue.RevenueRepository
import com.ongo.domain.revenueanalyzer.RevenueProjection
import com.ongo.domain.revenueanalyzer.RevenueProjectionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class RevenueForecasterUseCase(
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
    private val revenueRepository: RevenueRepository,
    private val projectionRepository: RevenueProjectionRepository,
) {

    private val log = LoggerFactory.getLogger(RevenueForecasterUseCase::class.java)

    fun getForecast(userId: Long, period: String?): RevenueForecastResponse {
        val effectivePeriod = period ?: "3M"

        // 저장된 예측 중 가장 최근 것을 반환
        val projections = projectionRepository.findByUserId(userId)
        if (projections.isNotEmpty()) {
            return buildForecastFromProjections(projections, effectivePeriod)
        }

        // 저장된 예측이 없으면 실제 수익 데이터 기반으로 간단한 통계 예측 생성
        return buildStatisticalForecast(userId, effectivePeriod)
    }

    @Transactional
    fun generateForecast(userId: Long, request: ForecastRequest): ForecastGenerateResponse {
        rateLimiter.checkRateLimit(userId)
        creditService.validateAndDeduct(userId, AiFeature.REVENUE_FORECAST)

        val to = LocalDate.now()
        val months = parsePeriodMonths(request.period)
        val from = to.minusMonths(months.toLong())
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM")

        // 실제 수익 데이터 수집
        val totalRevenue = revenueRepository.getTotalRevenue(userId, from, to)
        val currentMonthlyRevenue = if (months > 0) totalRevenue / months else 0L
        val dailyRevenue = revenueRepository.getDailyRevenue(userId, from, to)
        val platformRevenue = revenueRepository.getPlatformRevenue(userId, from, to)

        val revenueHistoryStr = dailyRevenue
            .groupBy { it.date.format(formatter) }
            .entries
            .sortedBy { it.key }
            .takeLast(6)
            .joinToString("\n") { (month, records) ->
                val monthTotal = records.sumOf { it.revenueMicro }
                "- $month: ${formatRevenue(monthTotal)}원"
            }.ifEmpty { "수익 데이터 없음" }

        val platformRevenueStr = platformRevenue.joinToString("\n") { pr ->
            "- ${pr.platform}: ${formatRevenue(pr.totalRevenueMicro)}원"
        }.ifEmpty { "플랫폼별 수익 데이터 없음" }

        val userPrompt = PromptTemplates.REVENUE_FORECAST_USER
            .replace("{period}", request.period)
            .replace("{currentRevenue}", formatRevenue(currentMonthlyRevenue))
            .replace("{revenueHistory}", revenueHistoryStr)
            .replace("{platformRevenue}", platformRevenueStr)

        try {
            val result = chatClientResolver.resolve(userId).prompt()
                .system(PromptTemplates.REVENUE_FORECAST_SYSTEM)
                .user(userPrompt)
                .call()
                .entity(RevenueForecastResult::class.java)
                ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")

            // AI 결과를 RevenueProjection으로 저장
            val projectionDate = LocalDate.now().toString()
            result.breakdowns.forEach { breakdown ->
                projectionRepository.save(
                    RevenueProjection(
                        userId = userId,
                        channelId = 0L,
                        source = breakdown.source,
                        currentMonthly = BigDecimal.valueOf(breakdown.currentMonthly),
                        projectedMonthly = BigDecimal.valueOf(breakdown.forecastMonthly),
                        confidence = (result.confidence * 100).toInt(),
                        projectionDate = projectionDate,
                        factors = listOf("AI 예측", "기간: ${request.period}"),
                    )
                )
            }

            val forecast = toForecastResponse(result, request.period)
            val balance = creditService.getBalance(userId)

            return ForecastGenerateResponse(
                forecast = forecast,
                creditsUsed = AiFeature.REVENUE_FORECAST.creditCost,
                creditsRemaining = balance.totalBalance,
            )
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("AI 수익 예측 실패, 크레딧 환불 처리: userId={}", userId, e)
            creditService.refundCredit(userId, AiFeature.REVENUE_FORECAST.creditCost, AiFeature.REVENUE_FORECAST.name)
            throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
        }
    }

    fun getHistory(userId: Long): RevenueForecastHistoryWrapper {
        val projections = projectionRepository.findByUserId(userId)
        if (projections.isEmpty()) {
            return RevenueForecastHistoryWrapper(emptyList())
        }

        // projectionDate 기준으로 그룹화하여 각각을 하나의 forecast로 변환
        val grouped = projections.groupBy { it.projectionDate }
        val forecasts = grouped.entries
            .sortedByDescending { it.key }
            .mapIndexed { index, (date, projs) ->
                buildForecastFromProjectionGroup(projs, date, index + 1L)
            }

        return RevenueForecastHistoryWrapper(forecasts)
    }

    private fun buildStatisticalForecast(userId: Long, period: String): RevenueForecastResponse {
        val to = LocalDate.now()
        val months = parsePeriodMonths(period)
        val from = to.minusMonths(maxOf(months.toLong(), 3))
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM")

        val dailyRevenue = revenueRepository.getDailyRevenue(userId, from, to)
        val platformRevenue = revenueRepository.getPlatformRevenue(userId, from, to)

        // 월별 집계
        val monthlyData = dailyRevenue
            .groupBy { it.date.format(formatter) }
            .entries
            .sortedBy { it.key }
            .map { (month, records) -> month to records.sumOf { it.revenueMicro } }

        val currentMonthly = monthlyData.lastOrNull()?.second ?: 0L
        val avgMonthly = if (monthlyData.isNotEmpty()) monthlyData.map { it.second }.average().toLong() else 0L

        // 성장률 계산
        val growthRate = if (monthlyData.size >= 2) {
            val first = monthlyData.first().second
            val last = monthlyData.last().second
            if (first > 0) ((last - first).toDouble() / first * 100) else 0.0
        } else 0.0

        val totalPlatformRevenue = platformRevenue.sumOf { it.totalRevenueMicro }
        val breakdowns = platformRevenue.map { pr ->
            val share = if (totalPlatformRevenue > 0) pr.totalRevenueMicro.toDouble() / totalPlatformRevenue * 100 else 0.0
            RevenueBreakdownResponse(
                source = pr.platform,
                currentMonthly = pr.totalRevenueMicro / maxOf(months, 1),
                forecastMonthly = (pr.totalRevenueMicro / maxOf(months, 1) * 1.1).toLong(),
                growthRate = 10.0,
                share = share,
            )
        }

        // 차트 데이터: 실측 + 간단한 선형 예측
        val chartData = monthlyData.map { (month, rev) ->
            RevenueDataPointResponse(month = month, actual = rev)
        }.toMutableList()

        val forecastMonths = parsePeriodMonths(period)
        val lastMonth = if (monthlyData.isNotEmpty()) LocalDate.parse("${monthlyData.last().first}-01") else to
        val monthlyGrowth = if (monthlyData.size >= 2) {
            (monthlyData.last().second - monthlyData.first().second).toDouble() / monthlyData.size
        } else 0.0

        for (i in 1..forecastMonths) {
            val forecastMonth = lastMonth.plusMonths(i.toLong())
            val forecastValue = (avgMonthly + monthlyGrowth * i).toLong().coerceAtLeast(0)
            chartData.add(
                RevenueDataPointResponse(
                    month = forecastMonth.format(formatter),
                    forecast = forecastValue,
                    lowerBound = (forecastValue * 0.85).toLong(),
                    upperBound = (forecastValue * 1.15).toLong(),
                )
            )
        }

        val forecastedMonthly = chartData.lastOrNull()?.forecast ?: avgMonthly

        return RevenueForecastResponse(
            id = 0,
            period = period,
            currentMonthlyRevenue = currentMonthly,
            forecastedMonthlyRevenue = forecastedMonthly ?: avgMonthly,
            growthRate = growthRate,
            breakdowns = breakdowns,
            scenarios = emptyList(),
            chartData = chartData,
            confidence = if (monthlyData.size >= 3) 0.6 else 0.3,
            generatedAt = LocalDateTime.now().toString(),
        )
    }

    private fun buildForecastFromProjections(projections: List<RevenueProjection>, period: String): RevenueForecastResponse {
        val latest = projections.sortedByDescending { it.createdAt }
        val latestDate = latest.firstOrNull()?.projectionDate ?: LocalDate.now().toString()
        val latestGroup = latest.filter { it.projectionDate == latestDate }
        return buildForecastFromProjectionGroup(latestGroup, latestDate, 1L)
    }

    private fun buildForecastFromProjectionGroup(
        projections: List<RevenueProjection>,
        date: String,
        id: Long,
    ): RevenueForecastResponse {
        val currentTotal = projections.sumOf { it.currentMonthly.toLong() }
        val projectedTotal = projections.sumOf { it.projectedMonthly.toLong() }
        val avgConfidence = if (projections.isNotEmpty()) projections.map { it.confidence }.average() else 0.0
        val growthRate = if (currentTotal > 0) (projectedTotal - currentTotal).toDouble() / currentTotal * 100 else 0.0

        val breakdowns = projections.map { proj ->
            val share = if (projectedTotal > 0) proj.projectedMonthly.toDouble() / projectedTotal.toBigDecimal().toDouble() * 100 else 0.0
            val growth = if (proj.currentMonthly > BigDecimal.ZERO) {
                (proj.projectedMonthly - proj.currentMonthly).toDouble() / proj.currentMonthly.toDouble() * 100
            } else 0.0
            RevenueBreakdownResponse(
                source = proj.source,
                currentMonthly = proj.currentMonthly.toLong(),
                forecastMonthly = proj.projectedMonthly.toLong(),
                growthRate = growth,
                share = share,
            )
        }

        return RevenueForecastResponse(
            id = id,
            period = "3M",
            currentMonthlyRevenue = currentTotal,
            forecastedMonthlyRevenue = projectedTotal,
            growthRate = growthRate,
            breakdowns = breakdowns,
            scenarios = emptyList(),
            chartData = emptyList(),
            confidence = avgConfidence / 100.0,
            generatedAt = date,
        )
    }

    private fun toForecastResponse(result: RevenueForecastResult, period: String): RevenueForecastResponse {
        val breakdowns = result.breakdowns.map { b ->
            RevenueBreakdownResponse(
                source = b.source,
                currentMonthly = b.currentMonthly,
                forecastMonthly = b.forecastMonthly,
                growthRate = b.growthRate,
                share = b.share,
            )
        }

        var scenarioId = 1L
        val scenarios = result.scenarios.map { s ->
            val forecastData = s.forecastData.map { dp ->
                RevenueDataPointResponse(
                    month = dp.month,
                    actual = dp.actual,
                    forecast = dp.forecast,
                    lowerBound = dp.lowerBound,
                    upperBound = dp.upperBound,
                )
            }
            ForecastScenarioResponse(
                id = scenarioId++,
                name = s.name,
                description = s.description,
                uploadFrequency = s.uploadFrequency,
                avgViewsPerVideo = s.avgViewsPerVideo,
                subscriberGrowthRate = s.subscriberGrowthRate,
                forecastData = forecastData,
                totalForecast = s.totalForecast,
            )
        }

        val chartData = result.chartData.map { dp ->
            RevenueDataPointResponse(
                month = dp.month,
                actual = dp.actual,
                forecast = dp.forecast,
                lowerBound = dp.lowerBound,
                upperBound = dp.upperBound,
            )
        }

        return RevenueForecastResponse(
            id = System.currentTimeMillis(),
            period = period,
            currentMonthlyRevenue = result.currentMonthlyRevenue,
            forecastedMonthlyRevenue = result.forecastedMonthlyRevenue,
            growthRate = result.growthRate,
            breakdowns = breakdowns,
            scenarios = scenarios,
            chartData = chartData,
            confidence = result.confidence,
            generatedAt = LocalDateTime.now().toString(),
        )
    }

    private fun parsePeriodMonths(period: String): Int = when (period) {
        "1M" -> 1
        "3M" -> 3
        "6M" -> 6
        "12M", "1Y" -> 12
        else -> 3
    }

    private fun formatRevenue(microAmount: Long): String {
        val amount = microAmount / 1_000_000.0
        return String.format("%,.0f", amount)
    }
}
