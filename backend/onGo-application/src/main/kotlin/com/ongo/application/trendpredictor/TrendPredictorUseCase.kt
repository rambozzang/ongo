package com.ongo.application.trendpredictor

import com.ongo.application.trendpredictor.dto.*
import com.ongo.domain.trendpredictor.TrendPredictionRepository
import com.ongo.domain.trendpredictor.TrendTopicRepository
import com.ongo.domain.trendpredictor.TrendPrediction
import com.ongo.domain.trendpredictor.TrendTopic
import org.springframework.stereotype.Service

@Service
class TrendPredictorUseCase(
    private val predictionRepository: TrendPredictionRepository,
    private val topicRepository: TrendTopicRepository,
) {

    fun getPredictions(userId: Long, category: String?): List<TrendPredictionResponse> {
        val predictions = if (category != null) {
            predictionRepository.findByCategory(userId, category)
        } else {
            predictionRepository.findByUserId(userId)
        }
        return predictions.map { it.toResponse() }
    }

    fun getTopics(predictionId: Long): List<TrendTopicResponse> {
        return topicRepository.findByPredictionId(predictionId).map { it.toResponse() }
    }

    fun getSummary(userId: Long): TrendPredictorSummaryResponse {
        val predictions = predictionRepository.findByUserId(userId)
        val rising = predictions.count { it.direction == "RISING" }
        return TrendPredictorSummaryResponse(
            totalPredictions = predictions.size,
            risingTrends = rising,
            accuracy = 84.5,
            topCategory = predictions.groupBy { it.category }.maxByOrNull { it.value.size }?.key ?: "",
            lastUpdated = predictions.maxByOrNull { it.createdAt ?: java.time.LocalDateTime.MIN }?.createdAt,
        )
    }

    private fun TrendPrediction.toResponse() = TrendPredictionResponse(
        id = id!!,
        keyword = keyword,
        category = category,
        platform = platform,
        currentScore = currentScore,
        predictedScore = predictedScore,
        confidence = confidence,
        direction = direction,
        peakDate = peakDate,
        createdAt = createdAt,
    )

    private fun TrendTopic.toResponse() = TrendTopicResponse(
        id = id!!,
        predictionId = predictionId,
        topic = topic,
        relatedKeywords = relatedKeywords,
        videoCount = videoCount,
        avgViews = avgViews,
        growthRate = growthRate,
    )
}
