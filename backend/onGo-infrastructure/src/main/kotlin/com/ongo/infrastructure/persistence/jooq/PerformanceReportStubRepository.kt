package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.performancereport.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class PerformanceReportJooqRepository(
    private val dsl: DSLContext,
) : PerformanceReportRepository {

    companion object {
        private val TABLE = DSL.table("performance_reports")
        private val PERIOD = DSL.field("period", String::class.java)
        private val START_DATE = DSL.field("start_date", LocalDate::class.java)
        private val END_DATE = DSL.field("end_date", LocalDate::class.java)
        private val TOTAL_VIEWS = DSL.field("total_views", Long::class.java)
        private val TOTAL_ENGAGEMENT = DSL.field("total_engagement", Long::class.java)
        private val TOP_VIDEO_ID = DSL.field("top_video_id", Long::class.java)
        private val TOP_VIDEO_TITLE = DSL.field("top_video_title", String::class.java)
        private val REPORT_URL = DSL.field("report_url", String::class.java)
    }

    override fun findById(id: Long): PerformanceReport? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toReport()

    override fun findByUserId(userId: Long): List<PerformanceReport> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId)).fetch { it.toReport() }

    override fun findByStatus(userId: Long, status: String): List<PerformanceReport> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .and(STATUS.eq(status))
            .fetch { it.toReport() }

    override fun save(report: PerformanceReport): PerformanceReport {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, report.userId)
            .set(TITLE, report.title)
            .set(PERIOD, report.period)
            .set(START_DATE, report.startDate)
            .set(END_DATE, report.endDate)
            .set(STATUS, report.status)
            .set(TOTAL_VIEWS, report.totalViews)
            .set(TOTAL_ENGAGEMENT, report.totalEngagement)
            .set(TOP_VIDEO_ID, report.topVideoId)
            .set(TOP_VIDEO_TITLE, report.topVideoTitle)
            .set(REPORT_URL, report.reportUrl)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(id)!!
    }

    override fun updateStatus(id: Long, status: String) {
        dsl.update(TABLE)
            .set(STATUS, status)
            .where(ID.eq(id))
            .execute()
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toReport(): PerformanceReport = PerformanceReport(
        id = get(ID),
        userId = get(USER_ID),
        title = get(TITLE),
        period = get(PERIOD) ?: "MONTHLY",
        startDate = localDate(START_DATE)!!,
        endDate = localDate(END_DATE)!!,
        status = get(STATUS) ?: "DRAFT",
        totalViews = get(TOTAL_VIEWS) ?: 0,
        totalEngagement = get(TOTAL_ENGAGEMENT) ?: 0,
        topVideoId = get("top_video_id")?.let { (it as Number).toLong() },
        topVideoTitle = get(TOP_VIDEO_TITLE),
        reportUrl = get(REPORT_URL),
        createdAt = localDateTime(CREATED_AT),
    )
}

@Repository
class ReportSectionJooqRepository(
    private val dsl: DSLContext,
) : ReportSectionRepository {

    companion object {
        private val TABLE = DSL.table("report_sections")
        private val REPORT_ID = DSL.field("report_id", Long::class.java)
        private val SECTION_TYPE = DSL.field("section_type", String::class.java)
        private val CONTENT = DSL.field("content", String::class.java)
        private val CHART_DATA = DSL.field("chart_data", String::class.java)
        private val SORT_ORDER = DSL.field("sort_order", Int::class.java)
    }

    override fun findByReportId(reportId: Long): List<ReportSection> =
        dsl.select().from(TABLE)
            .where(REPORT_ID.eq(reportId))
            .orderBy(SORT_ORDER)
            .fetch { it.toSection() }

    override fun save(section: ReportSection): ReportSection {
        val id = dsl.insertInto(TABLE)
            .set(REPORT_ID, section.reportId)
            .set(SECTION_TYPE, section.sectionType)
            .set(TITLE, section.title)
            .set(CONTENT, section.content)
            .set(CHART_DATA, DSL.field("?::jsonb", String::class.java, section.chartData))
            .set(SORT_ORDER, section.sortOrder)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()!!.toSection()
    }

    private fun Record.toSection(): ReportSection {
        val chartRaw = get("chart_data")
        return ReportSection(
            id = get(ID),
            reportId = get(REPORT_ID),
            sectionType = get(SECTION_TYPE),
            title = get(TITLE),
            content = get(CONTENT),
            chartData = when (chartRaw) {
                is String -> chartRaw
                else -> chartRaw?.toString()
            },
            sortOrder = get(SORT_ORDER) ?: 0,
        )
    }
}
