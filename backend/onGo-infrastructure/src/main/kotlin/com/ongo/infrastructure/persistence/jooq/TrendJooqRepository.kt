package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.trend.Trend
import com.ongo.domain.trend.TrendAlert
import com.ongo.domain.trend.TrendRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CATEGORY
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DATE
import com.ongo.infrastructure.persistence.jooq.Fields.ENABLED
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.KEYWORD
import com.ongo.infrastructure.persistence.jooq.Fields.METADATA
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.REGION
import com.ongo.infrastructure.persistence.jooq.Fields.SCORE
import com.ongo.infrastructure.persistence.jooq.Fields.SOURCE
import com.ongo.infrastructure.persistence.jooq.Fields.THRESHOLD
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.TREND_ALERTS
import com.ongo.infrastructure.persistence.jooq.Tables.TRENDS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class TrendJooqRepository(
    private val dsl: DSLContext,
) : TrendRepository {

    override fun findByDate(date: LocalDate, category: String?, source: String?): List<Trend> {
        var query = dsl.select().from(TRENDS).where(DATE.eq(date))
        if (category != null) query = query.and(CATEGORY.eq(category))
        if (source != null) query = query.and(SOURCE.eq(source))
        return query.orderBy(SCORE.desc()).fetch().map { it.toTrend() }
    }

    override fun searchByKeyword(keyword: String, limit: Int): List<Trend> =
        dsl.select().from(TRENDS)
            .where(KEYWORD.likeIgnoreCase("%$keyword%"))
            .orderBy(DATE.desc(), SCORE.desc())
            .limit(limit)
            .fetch().map { it.toTrend() }

    override fun saveBatch(trends: List<Trend>) {
        if (trends.isEmpty()) return
        val insert = dsl.insertInto(
            TRENDS, CATEGORY, KEYWORD, SCORE, SOURCE, PLATFORM, REGION, DATE, METADATA,
        )
        trends.forEach { t ->
            insert.values(t.category, t.keyword, t.score, t.source, t.platform, t.region, t.date, t.metadata)
        }
        insert.execute()
    }

    // Alerts

    override fun findAlertsByUserId(userId: Long): List<TrendAlert> =
        dsl.select().from(TREND_ALERTS).where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc()).fetch().map { it.toTrendAlert() }

    override fun findAlertById(id: Long): TrendAlert? =
        dsl.select().from(TREND_ALERTS).where(ID.eq(id)).fetchOne()?.toTrendAlert()

    override fun saveAlert(alert: TrendAlert): TrendAlert {
        val id = dsl.insertInto(TREND_ALERTS)
            .set(USER_ID, alert.userId)
            .set(KEYWORD, alert.keyword)
            .set(THRESHOLD, alert.threshold)
            .set(ENABLED, alert.enabled)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findAlertById(id)!!
    }

    override fun updateAlert(id: Long, keyword: String?, threshold: Double?, enabled: Boolean?) {
        val sets = mutableMapOf<org.jooq.Field<*>, Any?>()
        if (keyword != null) sets[KEYWORD] = keyword
        if (threshold != null) sets[THRESHOLD] = threshold
        if (enabled != null) sets[ENABLED] = enabled
        if (sets.isEmpty()) return
        dsl.update(TREND_ALERTS).set(sets).where(ID.eq(id)).execute()
    }

    override fun deleteAlert(id: Long) {
        dsl.deleteFrom(TREND_ALERTS).where(ID.eq(id)).execute()
    }

    private fun Record.toTrend(): Trend = Trend(
        id = get(ID),
        category = get(CATEGORY),
        keyword = get(KEYWORD),
        score = get(SCORE),
        source = get(SOURCE),
        platform = get(PLATFORM),
        region = get(REGION),
        date = localDate(DATE) ?: LocalDate.now(),
        metadata = get(METADATA),
        createdAt = localDateTime(CREATED_AT),
    )

    private fun Record.toTrendAlert(): TrendAlert = TrendAlert(
        id = get(ID),
        userId = get(USER_ID),
        keyword = get(KEYWORD),
        threshold = get(THRESHOLD),
        enabled = get(ENABLED),
        createdAt = localDateTime(CREATED_AT),
    )
}
