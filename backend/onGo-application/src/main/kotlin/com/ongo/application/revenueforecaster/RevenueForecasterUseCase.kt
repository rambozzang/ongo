package com.ongo.application.revenueforecaster

import com.ongo.application.revenueforecaster.dto.*
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class RevenueForecasterUseCase {

    fun getForecast(userId: Long, period: String?): RevenueForecastResponse {
        val effectivePeriod = period ?: "3M"
        return buildMockForecast(1L, effectivePeriod)
    }

    fun generateForecast(userId: Long, request: ForecastRequest): ForecastGenerateResponse {
        // AI 수익 예측 연동 포인트 - Phase 1에서는 mock 데이터 반환
        val forecast = buildMockForecast(2L, request.period)
        return ForecastGenerateResponse(
            forecast = forecast,
            creditsUsed = 10,
            creditsRemaining = 90,
        )
    }

    fun getHistory(userId: Long): RevenueForecastHistoryWrapper {
        val forecasts = listOf(
            buildMockForecast(1L, "3M"),
            buildMockForecast(2L, "6M"),
        )
        return RevenueForecastHistoryWrapper(forecasts)
    }

    private fun buildMockForecast(id: Long, period: String): RevenueForecastResponse {
        val now = LocalDateTime.now().toString()
        val chartData = listOf(
            RevenueDataPointResponse(month = "2026-01", actual = 1200000, forecast = null, lowerBound = null, upperBound = null),
            RevenueDataPointResponse(month = "2026-02", actual = 1350000, forecast = null, lowerBound = null, upperBound = null),
            RevenueDataPointResponse(month = "2026-03", actual = null, forecast = 1500000, lowerBound = 1350000, upperBound = 1650000),
            RevenueDataPointResponse(month = "2026-04", actual = null, forecast = 1680000, lowerBound = 1480000, upperBound = 1880000),
            RevenueDataPointResponse(month = "2026-05", actual = null, forecast = 1820000, lowerBound = 1570000, upperBound = 2070000),
        )

        val breakdowns = listOf(
            RevenueBreakdownResponse(source = "AD_REVENUE", currentMonthly = 800000, forecastMonthly = 1050000, growthRate = 31.25, share = 57.5),
            RevenueBreakdownResponse(source = "SPONSORSHIP", currentMonthly = 350000, forecastMonthly = 480000, growthRate = 37.14, share = 26.3),
            RevenueBreakdownResponse(source = "MEMBERSHIP", currentMonthly = 120000, forecastMonthly = 180000, growthRate = 50.0, share = 9.9),
            RevenueBreakdownResponse(source = "SUPER_CHAT", currentMonthly = 80000, forecastMonthly = 110000, growthRate = 37.5, share = 6.3),
        )

        val scenarios = listOf(
            ForecastScenarioResponse(
                id = 1, name = "보수적", description = "현재 성장률 유지 시나리오",
                uploadFrequency = 2, avgViewsPerVideo = 15000, subscriberGrowthRate = 3.5,
                forecastData = chartData, totalForecast = 5000000,
            ),
            ForecastScenarioResponse(
                id = 2, name = "낙관적", description = "업로드 빈도 증가 및 바이럴 시나리오",
                uploadFrequency = 4, avgViewsPerVideo = 30000, subscriberGrowthRate = 8.0,
                forecastData = chartData.map { it.copy(forecast = it.forecast?.let { f -> (f * 1.4).toLong() }) },
                totalForecast = 7000000,
            ),
            ForecastScenarioResponse(
                id = 3, name = "비관적", description = "업로드 빈도 감소 및 경쟁 심화 시나리오",
                uploadFrequency = 1, avgViewsPerVideo = 8000, subscriberGrowthRate = 1.0,
                forecastData = chartData.map { it.copy(forecast = it.forecast?.let { f -> (f * 0.7).toLong() }) },
                totalForecast = 3500000,
            ),
        )

        return RevenueForecastResponse(
            id = id,
            period = period,
            currentMonthlyRevenue = 1350000,
            forecastedMonthlyRevenue = 1820000,
            growthRate = 34.8,
            breakdowns = breakdowns,
            scenarios = scenarios,
            chartData = chartData,
            confidence = 0.78,
            generatedAt = now,
        )
    }
}
