package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.channelhealth.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Repository
class ChannelHealthMetricJooqRepository(
    private val dsl: DSLContext,
) : ChannelHealthMetricRepository {

    companion object {
        private val TABLE = DSL.table("channel_health_metrics")
        private val CHANNEL_ID = DSL.field("channel_id", Long::class.java)
        private val CHANNEL_NAME = DSL.field("channel_name", String::class.java)
        private val OVERALL_SCORE = DSL.field("overall_score", Int::class.java)
        private val GROWTH_SCORE = DSL.field("growth_score", Int::class.java)
        private val ENGAGEMENT_SCORE = DSL.field("engagement_score", Int::class.java)
        private val CONSISTENCY_SCORE = DSL.field("consistency_score", Int::class.java)
        private val AUDIENCE_SCORE = DSL.field("audience_score", Int::class.java)
        private val MONETIZATION_SCORE = DSL.field("monetization_score", Int::class.java)
        private val MEASURED_AT = DSL.field("measured_at", LocalDateTime::class.java)
    }

    override fun findById(id: Long): ChannelHealthMetric? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toMetric()

    override fun findByUserId(userId: Long): List<ChannelHealthMetric> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId))
            .orderBy(MEASURED_AT.desc())
            .fetch().map { it.toMetric() }

    override fun save(metric: ChannelHealthMetric): ChannelHealthMetric {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, metric.userId)
            .set(CHANNEL_ID, metric.channelId)
            .set(CHANNEL_NAME, metric.channelName)
            .set(PLATFORM, metric.platform)
            .set(OVERALL_SCORE, metric.overallScore)
            .set(GROWTH_SCORE, metric.growthScore)
            .set(ENGAGEMENT_SCORE, metric.engagementScore)
            .set(CONSISTENCY_SCORE, metric.consistencyScore)
            .set(AUDIENCE_SCORE, metric.audienceScore)
            .set(MONETIZATION_SCORE, metric.monetizationScore)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toMetric(): ChannelHealthMetric =
        ChannelHealthMetric(
            id = get(ID),
            userId = get(USER_ID),
            channelId = get(CHANNEL_ID),
            channelName = get(CHANNEL_NAME),
            platform = get(PLATFORM),
            overallScore = get(OVERALL_SCORE) ?: 0,
            growthScore = get(GROWTH_SCORE) ?: 0,
            engagementScore = get(ENGAGEMENT_SCORE) ?: 0,
            consistencyScore = get(CONSISTENCY_SCORE) ?: 0,
            audienceScore = get(AUDIENCE_SCORE) ?: 0,
            monetizationScore = get(MONETIZATION_SCORE) ?: 0,
            measuredAt = localDateTime(MEASURED_AT),
        )
}

@Repository
class HealthTrendJooqRepository(
    private val dsl: DSLContext,
) : HealthTrendRepository {

    companion object {
        private val TABLE = DSL.table("health_trends")
        private val METRIC_ID = DSL.field("metric_id", Long::class.java)
        private val CATEGORY = DSL.field("category", String::class.java)
        private val TREND_DATE = DSL.field("trend_date", LocalDate::class.java)
        private val SCORE = DSL.field("score", Int::class.java)
        private val CHANGE_VALUE = DSL.field("change_value", BigDecimal::class.java)
        private val RECOMMENDATION = DSL.field("recommendation", String::class.java)
    }

    override fun findByMetricId(metricId: Long): List<HealthTrend> =
        dsl.select().from(TABLE).where(METRIC_ID.eq(metricId))
            .orderBy(TREND_DATE.asc())
            .fetch().map { it.toTrend() }

    override fun save(trend: HealthTrend): HealthTrend {
        val id = dsl.insertInto(TABLE)
            .set(METRIC_ID, trend.metricId)
            .set(CATEGORY, trend.category)
            .set(TREND_DATE, trend.trendDate)
            .set(SCORE, trend.score)
            .set(CHANGE_VALUE, trend.changeValue)
            .set(RECOMMENDATION, trend.recommendation)
            .returningResult(Fields.ID)
            .fetchOne()!!.get(Fields.ID)

        return dsl.select().from(TABLE).where(Fields.ID.eq(id)).fetchOne()!!.toTrend()
    }

    private fun Record.toTrend(): HealthTrend =
        HealthTrend(
            id = get(Fields.ID),
            metricId = get(METRIC_ID),
            category = get(CATEGORY),
            trendDate = localDate(TREND_DATE) ?: LocalDate.now(),
            score = get(SCORE) ?: 0,
            changeValue = get(CHANGE_VALUE) ?: BigDecimal.ZERO,
            recommendation = get(RECOMMENDATION),
            createdAt = localDateTime(CREATED_AT),
        )
}
