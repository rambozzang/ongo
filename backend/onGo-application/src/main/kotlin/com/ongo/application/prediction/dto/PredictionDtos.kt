package com.ongo.application.prediction.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class PredictionResponse(
    val id: Long,
    val videoId: Long?,
    val predictedViews: Long?,
    val predictedLikes: Long?,
    val predictedEngagementRate: BigDecimal?,
    val confidenceScore: BigDecimal?,
    val optimalUploadTime: LocalDateTime?,
    val predictionData: String?,
    val actualViews: Long?,
    val actualLikes: Long?,
    val createdAt: LocalDateTime?,
)

data class CreatePredictionRequest(
    val videoId: Long? = null,
    val predictionData: String? = null,
)

data class UpdateActualsRequest(
    val actualViews: Long? = null,
    val actualLikes: Long? = null,
)

// ── Heatmap ────────────────────────────────────────
data class HeatmapCell(
    val dayOfWeek: Int,
    val dayLabel: String,
    val hour: Int,
    val value: Double,
)

data class PredictionHeatmapResponse(
    val data: List<HeatmapCell>,
)

// ── Optimal Times ──────────────────────────────────
data class PredictionOptimalTimeSlot(
    val dayOfWeek: Int,
    val dayLabel: String,
    val hour: Int,
    val timeLabel: String,
    val score: Double,
    val reason: String,
)

data class PredictionOptimalTimesResponse(
    val slots: List<PredictionOptimalTimeSlot>,
)

// ── Title Suggestion ───────────────────────────────
data class TitleSuggestion(
    val title: String,
    val score: Double,
    val reason: String,
)

data class TitleSuggestionsResponse(
    val suggestions: List<TitleSuggestion>,
)

// ── Tag Suggestion ─────────────────────────────────
data class TagSuggestion(
    val tag: String,
    val frequency: Int,
    val avgViews: Long,
)

data class TagSuggestionsResponse(
    val suggestions: List<TagSuggestion>,
)

// ── Competitor Comparison ──────────────────────────
data class CompetitorComparisonItem(
    val label: String,
    val predictedValue: Long,
    val actualValue: Long?,
    val accuracy: Double?,
)

data class CompetitorComparisonResponse(
    val videoId: Long,
    val comparisons: List<CompetitorComparisonItem>,
)

// ── History ────────────────────────────────────────
data class PredictionHistoryResponse(
    val items: List<PredictionResponse>,
    val total: Long,
)

// ── Summary ────────────────────────────────────────
data class PredictionSummaryResponse(
    val totalPredictions: Long,
    val averageAccuracy: Double,
    val bestPlatform: String,
    val predictionsWithActuals: Long,
    val avgConfidenceScore: Double,
)
