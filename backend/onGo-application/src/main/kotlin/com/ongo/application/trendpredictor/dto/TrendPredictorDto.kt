package com.ongo.application.trendpredictor.dto

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class TrendPredictionResponse(
    val id: Long,
    val keyword: String,
    val category: String,
    val platform: String,
    val currentScore: Int,
    val predictedScore: Int,
    val confidence: BigDecimal,
    val direction: String,
    val peakDate: LocalDate?,
    val createdAt: LocalDateTime?,
)

data class TrendTopicResponse(
    val id: Long,
    val predictionId: Long,
    val topic: String,
    val relatedKeywords: List<String>,
    val videoCount: Int,
    val avgViews: Long,
    val growthRate: BigDecimal,
)

data class TrendPredictorSummaryResponse(
    val totalPredictions: Int,
    val risingTrends: Int,
    val accuracy: Double,
    val topCategory: String,
    val lastUpdated: LocalDateTime?,
)

data class PredictRequest(
    val keyword: String,
    val platform: String,
)
