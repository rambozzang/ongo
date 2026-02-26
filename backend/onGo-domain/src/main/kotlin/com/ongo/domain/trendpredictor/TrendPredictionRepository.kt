package com.ongo.domain.trendpredictor

interface TrendPredictionRepository {
    fun findById(id: Long): TrendPrediction?
    fun findByUserId(userId: Long): List<TrendPrediction>
    fun findByCategory(userId: Long, category: String): List<TrendPrediction>
    fun save(prediction: TrendPrediction): TrendPrediction
}
