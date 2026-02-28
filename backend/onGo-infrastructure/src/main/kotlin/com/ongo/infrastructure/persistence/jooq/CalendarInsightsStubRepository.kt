package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.calendarinsights.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Repository
class CalendarInsightJooqRepository(
    private val dsl: DSLContext,
) : CalendarInsightRepository {

    companion object {
        private val TABLE = DSL.table("calendar_insights")
        private val DATE = DSL.field("date", LocalDate::class.java)
        private val DAY_OF_WEEK = DSL.field("day_of_week", String::class.java)
        private val HOUR = DSL.field("hour", Int::class.java)
        private val UPLOAD_COUNT = DSL.field("upload_count", Int::class.java)
        private val AVG_VIEWS = DSL.field("avg_views", Long::class.java)
        private val AVG_ENGAGEMENT = DSL.field("avg_engagement", BigDecimal::class.java)
        private val SCORE = DSL.field("score", Int::class.java)
    }

    override fun findByWorkspaceIdAndDateBetween(workspaceId: Long, startDate: LocalDate, endDate: LocalDate): List<CalendarInsight> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(DATE.ge(startDate))
            .and(DATE.le(endDate))
            .orderBy(DATE.asc(), HOUR.asc())
            .fetch().map { it.toInsight() }

    override fun findById(id: Long): CalendarInsight? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toInsight()

    override fun save(insight: CalendarInsight): CalendarInsight {
        val id = dsl.insertInto(TABLE)
            .set(WORKSPACE_ID, insight.workspaceId)
            .set(DATE, insight.date)
            .set(DAY_OF_WEEK, insight.dayOfWeek)
            .set(HOUR, insight.hour)
            .set(UPLOAD_COUNT, insight.uploadCount)
            .set(AVG_VIEWS, insight.avgViews)
            .set(AVG_ENGAGEMENT, insight.avgEngagement)
            .set(SCORE, insight.score)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    private fun Record.toInsight(): CalendarInsight =
        CalendarInsight(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            date = localDate(DATE) ?: LocalDate.now(),
            dayOfWeek = get(DAY_OF_WEEK),
            hour = get(HOUR),
            uploadCount = get(UPLOAD_COUNT) ?: 0,
            avgViews = get(AVG_VIEWS) ?: 0,
            avgEngagement = get(AVG_ENGAGEMENT) ?: BigDecimal.ZERO,
            score = get(SCORE) ?: 0,
            createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
            updatedAt = localDateTime(UPDATED_AT) ?: LocalDateTime.now(),
        )
}

@Repository
class OptimalTimeSlotJooqRepository(
    private val dsl: DSLContext,
) : OptimalTimeSlotRepository {

    companion object {
        private val TABLE = DSL.table("optimal_time_slots")
        private val DAY_OF_WEEK = DSL.field("day_of_week", String::class.java)
        private val HOUR = DSL.field("hour", Int::class.java)
        private val SCORE = DSL.field("score", Int::class.java)
        private val EXPECTED_VIEWS = DSL.field("expected_views", Long::class.java)
        private val EXPECTED_ENGAGEMENT = DSL.field("expected_engagement", BigDecimal::class.java)
        private val REASON = DSL.field("reason", String::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<OptimalTimeSlot> =
        dsl.select().from(TABLE).where(WORKSPACE_ID.eq(workspaceId))
            .orderBy(SCORE.desc())
            .fetch().map { it.toSlot() }

    override fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<OptimalTimeSlot> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(PLATFORM.eq(platform))
            .orderBy(SCORE.desc())
            .fetch().map { it.toSlot() }

    override fun save(slot: OptimalTimeSlot): OptimalTimeSlot {
        val id = dsl.insertInto(TABLE)
            .set(WORKSPACE_ID, slot.workspaceId)
            .set(PLATFORM, slot.platform)
            .set(DAY_OF_WEEK, slot.dayOfWeek)
            .set(HOUR, slot.hour)
            .set(SCORE, slot.score)
            .set(EXPECTED_VIEWS, slot.expectedViews)
            .set(EXPECTED_ENGAGEMENT, slot.expectedEngagement)
            .set(REASON, slot.reason)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()!!.toSlot()
    }

    private fun Record.toSlot(): OptimalTimeSlot =
        OptimalTimeSlot(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            platform = get(PLATFORM),
            dayOfWeek = get(DAY_OF_WEEK),
            hour = get(HOUR),
            score = get(SCORE) ?: 0,
            expectedViews = get(EXPECTED_VIEWS) ?: 0,
            expectedEngagement = get(EXPECTED_ENGAGEMENT) ?: BigDecimal.ZERO,
            reason = get(REASON),
            createdAt = localDateTime(Fields.CREATED_AT) ?: LocalDateTime.now(),
        )
}

@Repository
class UploadPatternJooqRepository(
    private val dsl: DSLContext,
) : UploadPatternRepository {

    companion object {
        private val TABLE = DSL.table("upload_patterns")
        private val TOTAL_UPLOADS = DSL.field("total_uploads", Int::class.java)
        private val AVG_UPLOADS_PER_WEEK = DSL.field("avg_uploads_per_week", BigDecimal::class.java)
        private val MOST_ACTIVE_DAY = DSL.field("most_active_day", String::class.java)
        private val MOST_ACTIVE_HOUR = DSL.field("most_active_hour", Int::class.java)
        private val CONSISTENCY = DSL.field("consistency", Int::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<UploadPattern> =
        dsl.select().from(TABLE).where(WORKSPACE_ID.eq(workspaceId))
            .orderBy(UPDATED_AT.desc())
            .fetch().map { it.toPattern() }

    override fun save(pattern: UploadPattern): UploadPattern {
        val id = dsl.insertInto(TABLE)
            .set(WORKSPACE_ID, pattern.workspaceId)
            .set(PLATFORM, pattern.platform)
            .set(TOTAL_UPLOADS, pattern.totalUploads)
            .set(AVG_UPLOADS_PER_WEEK, pattern.avgUploadsPerWeek)
            .set(MOST_ACTIVE_DAY, pattern.mostActiveDay)
            .set(MOST_ACTIVE_HOUR, pattern.mostActiveHour)
            .set(CONSISTENCY, pattern.consistency)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()!!.toPattern()
    }

    private fun Record.toPattern(): UploadPattern =
        UploadPattern(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            platform = get(PLATFORM),
            totalUploads = get(TOTAL_UPLOADS) ?: 0,
            avgUploadsPerWeek = get(AVG_UPLOADS_PER_WEEK) ?: BigDecimal.ZERO,
            mostActiveDay = get(MOST_ACTIVE_DAY),
            mostActiveHour = get("most_active_hour")?.let { (it as Number).toInt() },
            consistency = get(CONSISTENCY) ?: 0,
            createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
            updatedAt = localDateTime(UPDATED_AT) ?: LocalDateTime.now(),
        )
}
