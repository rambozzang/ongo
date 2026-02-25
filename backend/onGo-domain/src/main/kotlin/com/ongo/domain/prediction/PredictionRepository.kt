package com.ongo.domain.prediction

interface PredictionRepository {
    fun findById(id: Long): PerformancePrediction?
    fun findByUserId(userId: Long): List<PerformancePrediction>
    fun findByVideoId(videoId: Long): List<PerformancePrediction>
    fun save(prediction: PerformancePrediction): PerformancePrediction
    fun update(prediction: PerformancePrediction): PerformancePrediction
    fun delete(id: Long)
}
