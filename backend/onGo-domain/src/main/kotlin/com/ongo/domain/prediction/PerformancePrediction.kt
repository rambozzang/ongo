package com.ongo.domain.prediction

import java.math.BigDecimal
import java.time.LocalDateTime

data class PerformancePrediction(
    val id: Long? = null,
    val userId: Long,
    val videoId: Long? = null,
    val predictedViews: Long? = null,
    val predictedLikes: Long? = null,
    val predictedEngagementRate: BigDecimal? = null,
    val confidenceScore: BigDecimal? = null,
    val optimalUploadTime: LocalDateTime? = null,
    val predictionData: String? = null, // JSONB as String
    val actualViews: Long? = null,
    val actualLikes: Long? = null,
    val createdAt: LocalDateTime? = null,
)
