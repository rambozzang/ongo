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
