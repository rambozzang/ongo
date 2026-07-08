package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.channelhealth.ChannelHealthMetric
import com.ongo.domain.channelhealth.ChannelHealthMetricRepository
import com.ongo.infrastructure.persistence.jooq.Fields.AUDIENCE_SCORE
import org.jooq.impl.DSL
import com.ongo.infrastructure.persistence.jooq.Fields.CHANNEL_NAME
import com.ongo.infrastructure.persistence.jooq.Fields.CONSISTENCY_SCORE
import com.ongo.infrastructure.persistence.jooq.Fields.ENGAGEMENT_SCORE_INT
import com.ongo.infrastructure.persistence.jooq.Fields.GROWTH_SCORE
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.MEASURED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.MONETIZATION_SCORE
import com.ongo.infrastructure.persistence.jooq.Fields.OVERALL_SCORE
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.CHANNEL_HEALTH_METRICS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class ChannelHealthMetricJooqRepository(
    private val dsl: DSLContext,
) : ChannelHealthMetricRepository {

    override fun findById(id: Long): ChannelHealthMetric? =
        dsl.select()
            .from(CHANNEL_HEALTH_METRICS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toMetric()

    override fun findByUserId(userId: Long): List<ChannelHealthMetric> =
        dsl.select()
            .from(CHANNEL_HEALTH_METRICS)
            .where(USER_ID.eq(userId))
            .orderBy(MEASURED_AT.desc())
            .fetch()
            .map { it.toMetric() }

    override fun save(metric: ChannelHealthMetric): ChannelHealthMetric {
        val id = dsl.insertInto(CHANNEL_HEALTH_METRICS)
            .set(USER_ID, metric.userId)
            .set(DSL.field("channel_id", Long::class.java), metric.channelId)
            .set(CHANNEL_NAME, metric.channelName)
            .set(PLATFORM, metric.platform)
            .set(OVERALL_SCORE, metric.overallScore)
            .set(GROWTH_SCORE, metric.growthScore)
            .set(ENGAGEMENT_SCORE_INT, metric.engagementScore)
            .set(CONSISTENCY_SCORE, metric.consistencyScore)
            .set(AUDIENCE_SCORE, metric.audienceScore)
            .set(MONETIZATION_SCORE, metric.monetizationScore)
            .set(MEASURED_AT, metric.measuredAt)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)
        return findById(id)!!
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(CHANNEL_HEALTH_METRICS)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toMetric(): ChannelHealthMetric = ChannelHealthMetric(
        id = get(ID),
        userId = get(USER_ID)!!,
        channelId = get(DSL.field("channel_id", Long::class.java))!!,
        channelName = get(CHANNEL_NAME)!!,
        platform = get(PLATFORM)!!,
        overallScore = get(OVERALL_SCORE) ?: 0,
        growthScore = get(GROWTH_SCORE) ?: 0,
        engagementScore = get(ENGAGEMENT_SCORE_INT) ?: 0,
        consistencyScore = get(CONSISTENCY_SCORE) ?: 0,
        audienceScore = get(AUDIENCE_SCORE) ?: 0,
        monetizationScore = get(MONETIZATION_SCORE) ?: 0,
        measuredAt = localDateTime(MEASURED_AT),
    )
}
