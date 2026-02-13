package com.ongo.domain.analytics

import java.time.LocalDateTime

data class VideoPerformanceScore(
    val videoId: Long,
    val overallScore: Double,
    val viewVelocityScore: Double,
    val engagementScore: Double,
    val watchTimeScore: Double,
    val conversionScore: Double,
    val shareScore: Double,
    val isAnomaly: Boolean = false,
    val anomalyType: AnomalyType? = null,
    val predictedViews7d: Long = 0,
    val calculatedAt: LocalDateTime = LocalDateTime.now(),
)

enum class AnomalyType {
    VIRAL_SPIKE,
    ENGAGEMENT_SURGE,
    UNUSUAL_DROP,
    SHARE_SPIKE,
}
