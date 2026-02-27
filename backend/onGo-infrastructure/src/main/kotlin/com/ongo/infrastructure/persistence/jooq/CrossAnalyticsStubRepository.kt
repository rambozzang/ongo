package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.crossanalytics.CrossPlatformReport
import com.ongo.domain.crossanalytics.CrossAnalyticsRepository
import com.ongo.infrastructure.persistence.jooq.Fields.AUDIENCE_OVERLAP
import com.ongo.infrastructure.persistence.jooq.Fields.CONTENTS
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.GENERATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PERIOD
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM_SUMMARIES
import com.ongo.infrastructure.persistence.jooq.Fields.RECOMMENDATIONS
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.CROSS_PLATFORM_REPORTS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class CrossAnalyticsJooqRepository(
    private val dsl: DSLContext,
) : CrossAnalyticsRepository {

    override fun findById(id: Long): CrossPlatformReport? =
        dsl.select()
            .from(CROSS_PLATFORM_REPORTS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toReport()

    override fun findByUserId(userId: Long): List<CrossPlatformReport> =
        dsl.select()
            .from(CROSS_PLATFORM_REPORTS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toReport() }

    override fun save(report: CrossPlatformReport): CrossPlatformReport {
        val id = dsl.insertInto(CROSS_PLATFORM_REPORTS)
            .set(USER_ID, report.userId)
            .set(PERIOD, report.period)
            .set(CONTENTS, report.contents)
            .set(PLATFORM_SUMMARIES, report.platformSummaries)
            .set(AUDIENCE_OVERLAP, report.audienceOverlap)
            .set(RECOMMENDATIONS, report.recommendations)
            .set(GENERATED_AT, report.generatedAt ?: java.time.LocalDateTime.now())
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(report: CrossPlatformReport): CrossPlatformReport {
        dsl.update(CROSS_PLATFORM_REPORTS)
            .set(PERIOD, report.period)
            .set(CONTENTS, report.contents)
            .set(PLATFORM_SUMMARIES, report.platformSummaries)
            .set(AUDIENCE_OVERLAP, report.audienceOverlap)
            .set(RECOMMENDATIONS, report.recommendations)
            .where(ID.eq(report.id))
            .execute()

        return findById(report.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(CROSS_PLATFORM_REPORTS)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toReport(): CrossPlatformReport = CrossPlatformReport(
        id = get(ID),
        userId = get(USER_ID),
        period = get(PERIOD),
        contents = get(CONTENTS) ?: "[]",
        platformSummaries = get(PLATFORM_SUMMARIES) ?: "[]",
        audienceOverlap = get(AUDIENCE_OVERLAP) ?: "{}",
        recommendations = get(RECOMMENDATIONS) ?: "[]",
        generatedAt = localDateTime(GENERATED_AT),
    )
}
