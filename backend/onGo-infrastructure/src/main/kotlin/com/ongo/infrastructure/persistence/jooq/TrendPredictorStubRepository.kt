package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.trendpredictor.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Repository
class TrendPredictionJooqRepository(
    private val dsl: DSLContext,
) : TrendPredictionRepository {

    companion object {
        private val TABLE = DSL.table("trend_predictions")
        private val KEYWORD = DSL.field("keyword", String::class.java)
        private val CATEGORY = DSL.field("category", String::class.java)
        private val PLATFORM = DSL.field("platform", String::class.java)
        private val CURRENT_SCORE = DSL.field("current_score", Int::class.java)
        private val PREDICTED_SCORE = DSL.field("predicted_score", Int::class.java)
        private val CONFIDENCE = DSL.field("confidence", BigDecimal::class.java)
        private val DIRECTION = DSL.field("direction", String::class.java)
        private val PEAK_DATE = DSL.field("peak_date", LocalDate::class.java)
    }

    override fun findById(id: Long): TrendPrediction? =
        dsl.select().from(TABLE)
            .where(ID.eq(id))
            .fetchOne()?.toPrediction()

    override fun findByUserId(userId: Long): List<TrendPrediction> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .fetch { it.toPrediction() }

    override fun findByCategory(userId: Long, category: String): List<TrendPrediction> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .and(CATEGORY.eq(category))
            .fetch { it.toPrediction() }

    override fun save(prediction: TrendPrediction): TrendPrediction {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, prediction.userId)
            .set(KEYWORD, prediction.keyword)
            .set(CATEGORY, prediction.category)
            .set(PLATFORM, prediction.platform)
            .set(CURRENT_SCORE, prediction.currentScore)
            .set(PREDICTED_SCORE, prediction.predictedScore)
            .set(CONFIDENCE, prediction.confidence)
            .set(DIRECTION, prediction.direction)
            .set(PEAK_DATE, prediction.peakDate)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    private fun Record.toPrediction(): TrendPrediction =
        TrendPrediction(
            id = get(ID),
            userId = get(USER_ID),
            keyword = get(KEYWORD),
            category = get(CATEGORY),
            platform = get(PLATFORM),
            currentScore = get(CURRENT_SCORE) ?: 0,
            predictedScore = get(PREDICTED_SCORE) ?: 0,
            confidence = get(CONFIDENCE) ?: BigDecimal.ZERO,
            direction = get(DIRECTION) ?: "STABLE",
            peakDate = localDate(PEAK_DATE),
            createdAt = localDateTime(CREATED_AT),
        )
}

@Repository
class TrendTopicJooqRepository(
    private val dsl: DSLContext,
) : TrendTopicRepository {

    companion object {
        private val TABLE = DSL.table("trend_topics")
        private val PREDICTION_ID = DSL.field("prediction_id", Long::class.java)
        private val TOPIC = DSL.field("topic", String::class.java)
        private val RELATED_KEYWORDS = DSL.field("related_keywords", String::class.java)
        private val VIDEO_COUNT = DSL.field("video_count", Int::class.java)
        private val AVG_VIEWS = DSL.field("avg_views", Long::class.java)
        private val GROWTH_RATE = DSL.field("growth_rate", BigDecimal::class.java)
    }

    override fun findByPredictionId(predictionId: Long): List<TrendTopic> =
        dsl.select().from(TABLE)
            .where(PREDICTION_ID.eq(predictionId))
            .fetch { it.toTopic() }

    override fun save(topic: TrendTopic): TrendTopic {
        val relatedKeywordsJson = "[${topic.relatedKeywords.joinToString(",") { "\"$it\"" }}]"
        val id = dsl.insertInto(TABLE)
            .set(PREDICTION_ID, topic.predictionId)
            .set(TOPIC, topic.topic)
            .set(RELATED_KEYWORDS, DSL.field("?::jsonb", String::class.java, relatedKeywordsJson))
            .set(VIDEO_COUNT, topic.videoCount)
            .set(AVG_VIEWS, topic.avgViews)
            .set(GROWTH_RATE, topic.growthRate)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()!!.toTopic()
    }

    private fun Record.toTopic(): TrendTopic {
        val relatedRaw = get("related_keywords")
        val relatedList: List<String> = when (relatedRaw) {
            is String -> parseJsonArray(relatedRaw)
            else -> relatedRaw?.toString()?.let { parseJsonArray(it) } ?: emptyList()
        }
        return TrendTopic(
            id = get(ID),
            predictionId = get(PREDICTION_ID),
            topic = get(TOPIC),
            relatedKeywords = relatedList,
            videoCount = get(VIDEO_COUNT) ?: 0,
            avgViews = get(AVG_VIEWS) ?: 0,
            growthRate = get(GROWTH_RATE) ?: BigDecimal.ZERO,
        )
    }

    private fun parseJsonArray(json: String): List<String> {
        val trimmed = json.trim()
        if (trimmed == "[]" || trimmed.isBlank()) return emptyList()
        return trimmed.removePrefix("[").removeSuffix("]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
    }
}
