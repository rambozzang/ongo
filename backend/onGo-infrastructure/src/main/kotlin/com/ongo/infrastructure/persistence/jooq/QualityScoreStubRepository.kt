package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.qualityscore.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class QualityReportJooqRepository(
    private val dsl: DSLContext,
) : QualityReportRepository {

    companion object {
        private val TABLE = DSL.table("quality_reports")
        private val VIDEO_TITLE = DSL.field("video_title", String::class.java)
        private val OVERALL_SCORE = DSL.field("overall_score", Int::class.java)
        private val OVERALL_GRADE = DSL.field("overall_grade", String::class.java)
        private val METRICS = DSL.field("metrics", String::class.java)
        private val IMPROVEMENTS = DSL.field("improvements", String::class.java)
        private val COMPETITOR_AVG = DSL.field("competitor_avg", Int::class.java)
        private val CHECKED_AT = DSL.field("checked_at", LocalDateTime::class.java)
    }

    override fun findById(id: Long): QualityReport? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toReport()

    override fun findByUserId(userId: Long): List<QualityReport> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId)).fetch { it.toReport() }

    override fun save(report: QualityReport): QualityReport {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, report.userId)
            .set(VIDEO_ID, report.videoId)
            .set(VIDEO_TITLE, report.videoTitle)
            .set(OVERALL_SCORE, report.overallScore)
            .set(OVERALL_GRADE, report.overallGrade)
            .set(METRICS, DSL.field("?::jsonb", String::class.java, report.metrics))
            .set(IMPROVEMENTS, DSL.field("?::jsonb", String::class.java, report.improvements))
            .set(COMPETITOR_AVG, report.competitorAvg)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(id)!!
    }

    override fun update(report: QualityReport): QualityReport {
        dsl.update(TABLE)
            .set(VIDEO_ID, report.videoId)
            .set(VIDEO_TITLE, report.videoTitle)
            .set(OVERALL_SCORE, report.overallScore)
            .set(OVERALL_GRADE, report.overallGrade)
            .set(METRICS, DSL.field("?::jsonb", String::class.java, report.metrics))
            .set(IMPROVEMENTS, DSL.field("?::jsonb", String::class.java, report.improvements))
            .set(COMPETITOR_AVG, report.competitorAvg)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(report.id))
            .execute()
        return findById(report.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toReport(): QualityReport {
        val metricsRaw = get("metrics")
        val improvementsRaw = get("improvements")
        return QualityReport(
            id = get(ID),
            userId = get(USER_ID),
            videoId = get("video_id")?.let { (it as Number).toLong() },
            videoTitle = get(VIDEO_TITLE),
            overallScore = get(OVERALL_SCORE) ?: 0,
            overallGrade = get(OVERALL_GRADE) ?: "F",
            metrics = when (metricsRaw) {
                is String -> metricsRaw
                else -> metricsRaw?.toString() ?: "{}"
            },
            improvements = when (improvementsRaw) {
                is String -> improvementsRaw
                else -> improvementsRaw?.toString() ?: "[]"
            },
            competitorAvg = get(COMPETITOR_AVG) ?: 0,
            checkedAt = localDateTime(CHECKED_AT),
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
    }
}
