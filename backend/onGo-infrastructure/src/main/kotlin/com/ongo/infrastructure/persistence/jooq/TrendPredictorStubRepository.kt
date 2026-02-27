package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.trendpredictor.*
import org.springframework.stereotype.Repository

@Repository
class TrendPredictionStubRepository : TrendPredictionRepository {
    override fun findById(id: Long): TrendPrediction? = null
    override fun findByUserId(userId: Long): List<TrendPrediction> = emptyList()
    override fun findByCategory(userId: Long, category: String): List<TrendPrediction> = emptyList()
    override fun save(prediction: TrendPrediction): TrendPrediction = prediction.copy(id = 1)
}

@Repository
class TrendTopicStubRepository : TrendTopicRepository {
    override fun findByPredictionId(predictionId: Long): List<TrendTopic> = emptyList()
    override fun save(topic: TrendTopic): TrendTopic = topic.copy(id = 1)
}
