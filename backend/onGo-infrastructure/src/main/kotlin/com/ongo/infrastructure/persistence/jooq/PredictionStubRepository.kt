package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.prediction.*
import org.springframework.stereotype.Repository

@Repository
class PredictionStubRepository : PredictionRepository {
    override fun findById(id: Long): PerformancePrediction? = null
    override fun findByUserId(userId: Long): List<PerformancePrediction> = emptyList()
    override fun findByVideoId(videoId: Long): List<PerformancePrediction> = emptyList()
    override fun save(prediction: PerformancePrediction): PerformancePrediction = prediction.copy(id = 1)
    override fun update(prediction: PerformancePrediction): PerformancePrediction = prediction
    override fun delete(id: Long) {}
}
