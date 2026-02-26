package com.ongo.domain.trendpredictor

interface TrendTopicRepository {
    fun findByPredictionId(predictionId: Long): List<TrendTopic>
    fun save(topic: TrendTopic): TrendTopic
}
