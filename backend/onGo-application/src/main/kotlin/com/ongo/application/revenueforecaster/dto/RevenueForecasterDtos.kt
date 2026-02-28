package com.ongo.application.revenueforecaster.dto

data class RevenueDataPointResponse(
    val month: String,
    val actual: Long? = null,
    val forecast: Long? = null,
    val lowerBound: Long? = null,
    val upperBound: Long? = null,
)

data class RevenueBreakdownResponse(
    val source: String,
    val currentMonthly: Long,
    val forecastMonthly: Long,
    val growthRate: Double,
    val share: Double,
)

data class ForecastScenarioResponse(
    val id: Long,
    val name: String,
    val description: String,
    val uploadFrequency: Int,
    val avgViewsPerVideo: Long,
    val subscriberGrowthRate: Double,
    val forecastData: List<RevenueDataPointResponse>,
    val totalForecast: Long,
)

data class RevenueForecastResponse(
    val id: Long,
    val period: String,
    val currentMonthlyRevenue: Long,
    val forecastedMonthlyRevenue: Long,
    val growthRate: Double,
    val breakdowns: List<RevenueBreakdownResponse>,
    val scenarios: List<ForecastScenarioResponse>,
    val chartData: List<RevenueDataPointResponse>,
    val confidence: Double,
    val generatedAt: String,
)

data class ForecastRequest(
    val period: String,
    val includeScenarios: Boolean,
)

data class ForecastGenerateResponse(
    val forecast: RevenueForecastResponse,
    val creditsUsed: Int,
    val creditsRemaining: Int,
)

data class RevenueForecastHistoryWrapper(
    val forecasts: List<RevenueForecastResponse>,
)
