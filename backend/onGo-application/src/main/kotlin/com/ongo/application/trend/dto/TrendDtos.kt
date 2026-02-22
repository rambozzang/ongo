package com.ongo.application.trend.dto

data class TrendResponse(
    val id: Long,
    val keyword: String,
    val score: Double,
    val source: String,
    val category: String?,
    val platform: String?,
    val region: String,
    val date: String,
)

data class TrendAnalysisResponse(
    val summary: String,
    val recommendations: List<String>,
    val topKeywords: List<TrendResponse>,
)

data class TrendAlertResponse(
    val id: Long,
    val keyword: String,
    val threshold: Double,
    val enabled: Boolean,
    val createdAt: String?,
)

data class CreateTrendAlertRequest(
    val keyword: String,
    val threshold: Double = 50.0,
)

data class UpdateTrendAlertRequest(
    val keyword: String? = null,
    val threshold: Double? = null,
    val enabled: Boolean? = null,
)
