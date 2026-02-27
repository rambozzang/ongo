package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.prediction.PerformancePrediction
import com.ongo.domain.prediction.PredictionRepository
import com.ongo.infrastructure.persistence.jooq.Fields.ACTUAL_LIKES
import com.ongo.infrastructure.persistence.jooq.Fields.ACTUAL_VIEWS
import com.ongo.infrastructure.persistence.jooq.Fields.CONFIDENCE_SCORE
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.OPTIMAL_UPLOAD_TIME
import com.ongo.infrastructure.persistence.jooq.Fields.PREDICTED_ENGAGEMENT_RATE
import com.ongo.infrastructure.persistence.jooq.Fields.PREDICTED_LIKES
import com.ongo.infrastructure.persistence.jooq.Fields.PREDICTED_VIEWS
import com.ongo.infrastructure.persistence.jooq.Fields.PREDICTION_DATA
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Tables.PERFORMANCE_PREDICTIONS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class PredictionJooqRepository(
    private val dsl: DSLContext,
) : PredictionRepository {

    override fun findById(id: Long): PerformancePrediction? =
        dsl.select()
            .from(PERFORMANCE_PREDICTIONS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toPrediction()

    override fun findByUserId(userId: Long): List<PerformancePrediction> =
        dsl.select()
            .from(PERFORMANCE_PREDICTIONS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toPrediction() }

    override fun findByVideoId(videoId: Long): List<PerformancePrediction> =
        dsl.select()
            .from(PERFORMANCE_PREDICTIONS)
            .where(VIDEO_ID.eq(videoId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toPrediction() }

    override fun save(prediction: PerformancePrediction): PerformancePrediction {
        val id = dsl.insertInto(PERFORMANCE_PREDICTIONS)
            .set(USER_ID, prediction.userId)
            .set(VIDEO_ID, prediction.videoId)
            .set(PREDICTED_VIEWS, prediction.predictedViews)
            .set(PREDICTED_LIKES, prediction.predictedLikes)
            .set(PREDICTED_ENGAGEMENT_RATE, prediction.predictedEngagementRate)
            .set(CONFIDENCE_SCORE, prediction.confidenceScore)
            .set(OPTIMAL_UPLOAD_TIME, prediction.optimalUploadTime)
            .set(PREDICTION_DATA, prediction.predictionData)
            .set(ACTUAL_VIEWS, prediction.actualViews)
            .set(ACTUAL_LIKES, prediction.actualLikes)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(prediction: PerformancePrediction): PerformancePrediction {
        dsl.update(PERFORMANCE_PREDICTIONS)
            .set(PREDICTED_VIEWS, prediction.predictedViews)
            .set(PREDICTED_LIKES, prediction.predictedLikes)
            .set(PREDICTED_ENGAGEMENT_RATE, prediction.predictedEngagementRate)
            .set(CONFIDENCE_SCORE, prediction.confidenceScore)
            .set(OPTIMAL_UPLOAD_TIME, prediction.optimalUploadTime)
            .set(PREDICTION_DATA, prediction.predictionData)
            .set(ACTUAL_VIEWS, prediction.actualViews)
            .set(ACTUAL_LIKES, prediction.actualLikes)
            .where(ID.eq(prediction.id))
            .execute()

        return findById(prediction.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(PERFORMANCE_PREDICTIONS)
            .where(ID.eq(id))
            .execute()
    }

    override fun findByUserIdOrderByCreatedAtDesc(userId: Long, limit: Int): List<PerformancePrediction> =
        dsl.select()
            .from(PERFORMANCE_PREDICTIONS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .limit(limit)
            .fetch()
            .map { it.toPrediction() }

    override fun countByUserId(userId: Long): Long =
        dsl.selectCount()
            .from(PERFORMANCE_PREDICTIONS)
            .where(USER_ID.eq(userId))
            .fetchOne(0, Long::class.java) ?: 0L

    private fun Record.toPrediction(): PerformancePrediction = PerformancePrediction(
        id = get(ID),
        userId = get(USER_ID),
        videoId = get(VIDEO_ID),
        predictedViews = get(PREDICTED_VIEWS),
        predictedLikes = get(PREDICTED_LIKES),
        predictedEngagementRate = get(PREDICTED_ENGAGEMENT_RATE),
        confidenceScore = get(CONFIDENCE_SCORE),
        optimalUploadTime = localDateTime(OPTIMAL_UPLOAD_TIME),
        predictionData = get(PREDICTION_DATA),
        actualViews = get(ACTUAL_VIEWS),
        actualLikes = get(ACTUAL_LIKES),
        createdAt = localDateTime(CREATED_AT),
    )
}
