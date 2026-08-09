package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.shorts.ShortsRenderJob
import com.ongo.domain.ugc.shorts.ShortsRenderJobRepository
import com.ongo.domain.ugc.shorts.ShortsRenderJobStatus
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/** jOOQ generated tables are intentionally not required for this additive job table. */
@Repository
class ShortsRenderJobJooqRepository(
    private val dsl: DSLContext,
) : ShortsRenderJobRepository {

    override fun findById(id: String): ShortsRenderJob? =
        dsl.select()
            .from(TABLE)
            .where(ID.eq(id))
            .fetchOne()
            ?.toJob()

    override fun findByRunAndClip(runId: Long, clipId: Long): ShortsRenderJob? =
        dsl.select()
            .from(TABLE)
            .where(RUN_ID.eq(runId))
            .and(CLIP_ID.eq(clipId))
            .fetchOne()
            ?.toJob()

    override fun findByStatus(status: ShortsRenderJobStatus, limit: Int): List<ShortsRenderJob> =
        dsl.select()
            .from(TABLE)
            .where(STATUS.eq(status.name))
            .orderBy(UPDATED_AT.asc())
            .limit(limit.coerceIn(1, 200))
            .fetch()
            .map { it.toJob() }

    override fun saveIfAbsent(job: ShortsRenderJob): ShortsRenderJob {
        dsl.insertInto(TABLE)
            .set(ID, job.id)
            .set(RUN_ID, job.runId)
            .set(CLIP_ID, job.clipId)
            .set(STATUS, job.status.name)
            .set(PROGRESS, job.progress)
            .set(VIDEO_ID, job.videoId)
            .set(FAILURE_REASON, job.failureReason)
            .set(ATTEMPT_COUNT, job.attemptCount)
            .set(CREATED_AT, localDateTime(job.createdAt))
            .set(UPDATED_AT, localDateTime(job.updatedAt))
            .set(STARTED_AT, job.startedAt?.let(::localDateTime))
            .set(COMPLETED_AT, job.completedAt?.let(::localDateTime))
            .onConflict(RUN_ID, CLIP_ID)
            .doNothing()
            .execute()
        return findByRunAndClip(job.runId, job.clipId)
            ?: error("렌더 job 저장 후 조회할 수 없습니다: runId=${job.runId}, clipId=${job.clipId}")
    }

    override fun claimQueued(id: String, startedAt: Instant): ShortsRenderJob? {
        val affected = dsl.update(TABLE)
            .set(STATUS, ShortsRenderJobStatus.RUNNING.name)
            .set(PROGRESS, 0)
            .set(ATTEMPT_COUNT, ATTEMPT_COUNT.plus(1))
            .set(STARTED_AT, localDateTime(startedAt))
            .set(COMPLETED_AT, null as LocalDateTime?)
            .set(FAILURE_REASON, null as String?)
            .set(UPDATED_AT, localDateTime(startedAt))
            .where(ID.eq(id))
            .and(STATUS.eq(ShortsRenderJobStatus.QUEUED.name))
            .execute()
        return if (affected == 1) findById(id) else null
    }

    override fun update(job: ShortsRenderJob): ShortsRenderJob {
        dsl.update(TABLE)
            .set(STATUS, job.status.name)
            .set(PROGRESS, job.progress)
            .set(VIDEO_ID, job.videoId)
            .set(FAILURE_REASON, job.failureReason)
            .set(ATTEMPT_COUNT, job.attemptCount)
            .set(UPDATED_AT, localDateTime(job.updatedAt))
            .set(STARTED_AT, job.startedAt?.let(::localDateTime))
            .set(COMPLETED_AT, job.completedAt?.let(::localDateTime))
            .where(ID.eq(job.id))
            .execute()
        return findById(job.id) ?: error("렌더 job을 찾을 수 없습니다: ${job.id}")
    }

    private fun Record.toJob(): ShortsRenderJob = ShortsRenderJob(
        id = get(ID)!!,
        runId = get(RUN_ID)!!,
        clipId = get(CLIP_ID)!!,
        status = ShortsRenderJobStatus.valueOf(get(STATUS)!!),
        progress = get(PROGRESS),
        videoId = get(VIDEO_ID),
        failureReason = get(FAILURE_REASON),
        attemptCount = get(ATTEMPT_COUNT)!!,
        createdAt = instant(CREATED_AT)!!,
        updatedAt = instant(UPDATED_AT)!!,
        startedAt = instant(STARTED_AT),
        completedAt = instant(COMPLETED_AT),
    )

    private fun Record.instant(field: org.jooq.Field<LocalDateTime>): Instant? =
        get(field)?.atZone(ZoneOffset.UTC)?.toInstant()

    private fun localDateTime(value: Instant): LocalDateTime =
        LocalDateTime.ofInstant(value, ZoneOffset.UTC)

    companion object {
        private val TABLE = DSL.table(DSL.name("shorts_render_jobs"))
        private val ID = DSL.field(DSL.name("id"), String::class.java)
        private val RUN_ID = DSL.field(DSL.name("run_id"), Long::class.java)
        private val CLIP_ID = DSL.field(DSL.name("clip_id"), Long::class.java)
        private val STATUS = DSL.field(DSL.name("status"), String::class.java)
        private val PROGRESS = DSL.field(DSL.name("progress"), Int::class.java)
        private val VIDEO_ID = DSL.field(DSL.name("video_id"), Long::class.java)
        private val FAILURE_REASON = DSL.field(DSL.name("failure_reason"), String::class.java)
        private val ATTEMPT_COUNT = DSL.field(DSL.name("attempt_count"), Int::class.java)
        private val CREATED_AT = DSL.field(DSL.name("created_at"), LocalDateTime::class.java)
        private val UPDATED_AT = DSL.field(DSL.name("updated_at"), LocalDateTime::class.java)
        private val STARTED_AT = DSL.field(DSL.name("started_at"), LocalDateTime::class.java)
        private val COMPLETED_AT = DSL.field(DSL.name("completed_at"), LocalDateTime::class.java)
    }
}
