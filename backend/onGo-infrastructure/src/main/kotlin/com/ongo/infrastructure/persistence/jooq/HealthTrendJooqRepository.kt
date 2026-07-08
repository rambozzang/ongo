package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.channelhealth.HealthTrend
import com.ongo.domain.channelhealth.HealthTrendRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CATEGORY
import com.ongo.infrastructure.persistence.jooq.Fields.CHANGE_VALUE
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.METRIC_ID
import com.ongo.infrastructure.persistence.jooq.Fields.RECOMMENDATION
import com.ongo.infrastructure.persistence.jooq.Fields.SCORE_INT
import com.ongo.infrastructure.persistence.jooq.Fields.TREND_DATE
import com.ongo.infrastructure.persistence.jooq.Tables.HEALTH_TRENDS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class HealthTrendJooqRepository(
    private val dsl: DSLContext,
) : HealthTrendRepository {

    override fun findByMetricId(metricId: Long): List<HealthTrend> =
        dsl.select()
            .from(HEALTH_TRENDS)
            .where(METRIC_ID.eq(metricId))
            .orderBy(TREND_DATE.desc())
            .fetch()
            .map { it.toTrend() }

    override fun save(trend: HealthTrend): HealthTrend {
        val id = dsl.insertInto(HEALTH_TRENDS)
            .set(METRIC_ID, trend.metricId)
            .set(CATEGORY, trend.category)
            .set(TREND_DATE, trend.trendDate)
            .set(SCORE_INT, trend.score)
            .set(CHANGE_VALUE, trend.changeValue)
            .set(RECOMMENDATION, trend.recommendation)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)
        return findById(id)!!
    }

    private fun findById(id: Long): HealthTrend? =
        dsl.select()
            .from(HEALTH_TRENDS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toTrend()

    private fun Record.toTrend(): HealthTrend = HealthTrend(
        id = get(ID),
        metricId = get(METRIC_ID)!!,
        category = get(CATEGORY)!!,
        trendDate = localDate(TREND_DATE)!!,
        score = get(SCORE_INT) ?: 0,
        changeValue = get(CHANGE_VALUE) ?: java.math.BigDecimal.ZERO,
        recommendation = get(RECOMMENDATION),
        createdAt = localDateTime(CREATED_AT),
    )
}
