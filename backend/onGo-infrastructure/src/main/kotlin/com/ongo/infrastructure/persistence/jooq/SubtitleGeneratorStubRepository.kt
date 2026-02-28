package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.subtitlegenerator.*
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.LANGUAGE
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant

@Repository
class SubtitleJobJooqRepository(
    private val dsl: DSLContext,
) : SubtitleJobRepository {

    companion object {
        private val TABLE = DSL.table("subtitle_jobs")
        private val VIDEO_TITLE = DSL.field("video_title", String::class.java)
        private val PROGRESS = DSL.field("progress", Int::class.java)
        private val SUBTITLE_URL = DSL.field("subtitle_url", String::class.java)
        private val WORD_COUNT = DSL.field("word_count", Int::class.java)
        private val DURATION = DSL.field("duration", Int::class.java)
        private val CREATED_AT = DSL.field("created_at", java.time.LocalDateTime::class.java)
        private val COMPLETED_AT = DSL.field("completed_at", java.time.LocalDateTime::class.java)
    }

    override fun findByUserId(userId: Long): List<SubtitleJob> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch().map { it.toJob() }

    override fun findById(id: Long): SubtitleJob? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toJob()

    override fun findByUserIdAndStatus(userId: Long, status: String): List<SubtitleJob> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .and(STATUS.eq(status))
            .orderBy(CREATED_AT.desc())
            .fetch().map { it.toJob() }

    override fun save(job: SubtitleJob): SubtitleJob {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, job.userId)
            .set(VIDEO_ID, job.videoId)
            .set(VIDEO_TITLE, job.videoTitle)
            .set(PLATFORM, job.platform)
            .set(LANGUAGE, job.language)
            .set(STATUS, job.status)
            .set(PROGRESS, job.progress)
            .set(SUBTITLE_URL, job.subtitleUrl)
            .set(WORD_COUNT, job.wordCount)
            .set(DURATION, job.duration)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    override fun updateStatus(id: Long, status: String, progress: Int) {
        dsl.update(TABLE)
            .set(STATUS, status)
            .set(PROGRESS, progress)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toJob(): SubtitleJob {
        val createdAtRaw = get("created_at")
        val completedAtRaw = get("completed_at")
        return SubtitleJob(
            id = get(ID),
            userId = get(USER_ID),
            videoId = get(VIDEO_ID),
            videoTitle = get(VIDEO_TITLE),
            platform = get(PLATFORM),
            language = get(LANGUAGE),
            status = get(STATUS) ?: "PENDING",
            progress = get(PROGRESS) ?: 0,
            subtitleUrl = get(SUBTITLE_URL),
            wordCount = get(WORD_COUNT) ?: 0,
            duration = get(DURATION) ?: 0,
            createdAt = when (createdAtRaw) {
                is java.time.LocalDateTime -> createdAtRaw.atZone(java.time.ZoneId.systemDefault()).toInstant()
                is java.sql.Timestamp -> createdAtRaw.toInstant()
                else -> Instant.now()
            },
            completedAt = when (completedAtRaw) {
                is java.time.LocalDateTime -> completedAtRaw.atZone(java.time.ZoneId.systemDefault()).toInstant()
                is java.sql.Timestamp -> completedAtRaw.toInstant()
                else -> null
            },
        )
    }
}

@Repository
class SubtitleSegmentJooqRepository(
    private val dsl: DSLContext,
) : SubtitleSegmentRepository {

    companion object {
        private val TABLE = DSL.table("subtitle_segments")
        private val JOB_ID = DSL.field("job_id", Long::class.java)
        private val START_TIME = DSL.field("start_time", BigDecimal::class.java)
        private val END_TIME = DSL.field("end_time", BigDecimal::class.java)
        private val TEXT = DSL.field("text", String::class.java)
        private val CONFIDENCE = DSL.field("confidence", BigDecimal::class.java)
    }

    override fun findByJobId(jobId: Long): List<SubtitleSegment> =
        dsl.select().from(TABLE).where(JOB_ID.eq(jobId))
            .orderBy(START_TIME.asc())
            .fetch().map { it.toSegment() }

    override fun saveAll(segments: List<SubtitleSegment>): List<SubtitleSegment> {
        if (segments.isEmpty()) return emptyList()

        val jobId = segments.first().jobId
        segments.forEach { segment ->
            dsl.insertInto(TABLE)
                .set(JOB_ID, segment.jobId)
                .set(START_TIME, segment.startTime)
                .set(END_TIME, segment.endTime)
                .set(TEXT, segment.text)
                .set(CONFIDENCE, segment.confidence)
                .execute()
        }

        return findByJobId(jobId)
    }

    private fun Record.toSegment(): SubtitleSegment =
        SubtitleSegment(
            id = get(Fields.ID),
            jobId = get(JOB_ID),
            startTime = get(START_TIME) ?: BigDecimal.ZERO,
            endTime = get(END_TIME) ?: BigDecimal.ZERO,
            text = get(TEXT),
            confidence = get(CONFIDENCE) ?: BigDecimal.ZERO,
        )
}
