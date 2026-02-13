package com.ongo.application.analytics.dto

import com.ongo.domain.analytics.AnomalyType
import java.time.LocalDateTime

data class PerformanceScoreResponse(
    val videoId: Long,
    val overallScore: Double,
    val breakdown: Map<String, Double>,
    val percentileRank: Double,
    val trend: String,
    val isAnomaly: Boolean,
    val anomalyDescription: String?,
    val prediction7d: Long,
)

data class AnomalyResponse(
    val videoId: Long,
    val videoTitle: String?,
    val anomalyType: AnomalyType,
    val severity: String,
    val description: String,
    val detectedAt: LocalDateTime,
)

data class AnomalyListResponse(
    val anomalies: List<AnomalyResponse>,
)
