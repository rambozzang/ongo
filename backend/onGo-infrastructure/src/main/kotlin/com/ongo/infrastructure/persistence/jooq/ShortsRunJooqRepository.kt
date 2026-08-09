package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.shorts.PipelineRun
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.ugc.shorts.PipelineRunStatus
import com.ongo.domain.ugc.shorts.PipelineStage
import com.ongo.infrastructure.persistence.jooq.Fields.CLIP_COUNT
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.CURRENT_STAGE
import com.ongo.infrastructure.persistence.jooq.Fields.ERROR_MESSAGE
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.IDEMPOTENCY_KEY
import com.ongo.infrastructure.persistence.jooq.Fields.SOURCE_VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.TEMPLATE_ID
import com.ongo.infrastructure.persistence.jooq.Fields.CROP_JSON
import com.ongo.infrastructure.persistence.jooq.Fields.AUTO_SCHEDULE
import com.ongo.infrastructure.persistence.jooq.Fields.AUTO_SCHEDULE_INTERVAL_HOURS
import com.ongo.infrastructure.persistence.jooq.Fields.AUTO_SCHEDULE_PLATFORMS
import com.ongo.infrastructure.persistence.jooq.Fields.AUTO_SCHEDULE_START_AT
import com.ongo.infrastructure.persistence.jooq.Fields.TRANSCRIPT_TEXT
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VERSION
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_SHORTS_PIPELINE_RUNS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.ZoneOffset

@Repository
class ShortsRunJooqRepository(
    private val dsl: DSLContext,
) : PipelineRunRepository {

    override fun save(run: PipelineRun): PipelineRun {
        val id = dsl.insertInto(UGC_SHORTS_PIPELINE_RUNS)
            .set(WORKSPACE_ID, run.workspaceId)
            .set(USER_ID, run.userId)
            .set(SOURCE_VIDEO_ID, run.sourceVideoId)
            .set(TEMPLATE_ID, run.templateId)
            .set(AUTO_SCHEDULE, run.autoSchedule)
            .set(AUTO_SCHEDULE_START_AT, run.autoScheduleStartAt?.let(::localDateTime))
            .set(AUTO_SCHEDULE_INTERVAL_HOURS, run.autoScheduleIntervalHours)
            .set(AUTO_SCHEDULE_PLATFORMS, run.autoSchedulePlatforms.takeIf { it.isNotEmpty() }?.joinToString("\n"))
            .set(IDEMPOTENCY_KEY, run.idempotencyKey)
            .set(REQUEST_HASH, run.requestHash)
            .set(STATUS, run.status.name)
            .set(CURRENT_STAGE, run.currentStage?.name)
            .set(TRANSCRIPT_TEXT, run.transcriptText)
            .set(CROP_JSON, run.cropJson)
            .set(CLIP_COUNT, run.clipCount)
            .set(ERROR_MESSAGE, run.errorMessage)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun saveIdempotently(run: PipelineRun): PipelineRunRepository.SaveResult {
        val key = run.idempotencyKey
            ?: return PipelineRunRepository.SaveResult(save(run), created = true)
        val insertedId = dsl.insertInto(UGC_SHORTS_PIPELINE_RUNS)
            .set(WORKSPACE_ID, run.workspaceId)
            .set(USER_ID, run.userId)
            .set(SOURCE_VIDEO_ID, run.sourceVideoId)
            .set(TEMPLATE_ID, run.templateId)
            .set(AUTO_SCHEDULE, run.autoSchedule)
            .set(AUTO_SCHEDULE_START_AT, run.autoScheduleStartAt?.let(::localDateTime))
            .set(AUTO_SCHEDULE_INTERVAL_HOURS, run.autoScheduleIntervalHours)
            .set(AUTO_SCHEDULE_PLATFORMS, run.autoSchedulePlatforms.takeIf { it.isNotEmpty() }?.joinToString("\n"))
            .set(IDEMPOTENCY_KEY, key)
            .set(REQUEST_HASH, run.requestHash)
            .set(STATUS, run.status.name)
            .set(CURRENT_STAGE, run.currentStage?.name)
            .set(TRANSCRIPT_TEXT, run.transcriptText)
            .set(CROP_JSON, run.cropJson)
            .set(CLIP_COUNT, run.clipCount)
            .set(ERROR_MESSAGE, run.errorMessage)
            .onConflict(USER_ID, IDEMPOTENCY_KEY)
            .doNothing()
            .returningResult(ID)
            .fetchOne()
            ?.get(ID)
        val id = insertedId ?: dsl.select(ID)
            .from(UGC_SHORTS_PIPELINE_RUNS)
            .where(USER_ID.eq(run.userId))
            .and(IDEMPOTENCY_KEY.eq(key))
            .fetchOne(ID)
            ?: error("쇼츠 멱등 키 작업을 저장한 뒤 조회할 수 없습니다")
        return PipelineRunRepository.SaveResult(
            run = findById(id) ?: error("쇼츠 파이프라인 실행을 찾을 수 없습니다: $id"),
            created = insertedId != null,
        )
    }

    override fun findByUserIdAndIdempotencyKey(userId: Long, idempotencyKey: String): PipelineRun? =
        dsl.select()
            .from(UGC_SHORTS_PIPELINE_RUNS)
            .where(USER_ID.eq(userId))
            .and(IDEMPOTENCY_KEY.eq(idempotencyKey))
            .fetchOne()
            ?.toPipelineRun()

    override fun update(run: PipelineRun): PipelineRun {
        // 낙관적 락: 로드 시점 version과 일치할 때만 갱신하고 version을 증가시킨다.
        val affected = dsl.update(UGC_SHORTS_PIPELINE_RUNS)
            .set(TEMPLATE_ID, run.templateId)
            .set(AUTO_SCHEDULE, run.autoSchedule)
            .set(AUTO_SCHEDULE_START_AT, run.autoScheduleStartAt?.let(::localDateTime))
            .set(AUTO_SCHEDULE_INTERVAL_HOURS, run.autoScheduleIntervalHours)
            .set(AUTO_SCHEDULE_PLATFORMS, run.autoSchedulePlatforms.takeIf { it.isNotEmpty() }?.joinToString("\n"))
            .set(STATUS, run.status.name)
            .set(CURRENT_STAGE, run.currentStage?.name)
            .set(TRANSCRIPT_TEXT, run.transcriptText)
            .set(CROP_JSON, run.cropJson)
            .set(CLIP_COUNT, run.clipCount)
            .set(ERROR_MESSAGE, run.errorMessage)
            .set(VERSION, run.version + 1)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(run.id))
            .and(VERSION.eq(run.version))
            .execute()

        if (affected == 0) {
            throw IllegalStateException("실행이 다른 곳에서 수정되었습니다. 새로고침 후 다시 시도해 주세요")
        }
        return findById(run.id)!!
    }

    override fun findById(id: Long): PipelineRun? =
        dsl.select()
            .from(UGC_SHORTS_PIPELINE_RUNS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toPipelineRun()

    override fun findByStatus(status: PipelineRunStatus, limit: Int): List<PipelineRun> =
        dsl.select()
            .from(UGC_SHORTS_PIPELINE_RUNS)
            .where(STATUS.eq(status.name))
            .orderBy(UPDATED_AT.asc())
            .limit(limit.coerceIn(1, 200))
            .fetch()
            .map { it.toPipelineRun() }

    override fun findByWorkspace(workspaceId: Long, offset: Int, limit: Int): List<PipelineRun> =
        dsl.select()
            .from(UGC_SHORTS_PIPELINE_RUNS)
            .where(WORKSPACE_ID.eq(workspaceId))
            .orderBy(CREATED_AT.desc())
            .offset(offset)
            .limit(limit)
            .fetch()
            .map { it.toPipelineRun() }

    override fun countByWorkspace(workspaceId: Long): Long =
        dsl.fetchCount(UGC_SHORTS_PIPELINE_RUNS, WORKSPACE_ID.eq(workspaceId)).toLong()

    override fun delete(id: Long): Boolean =
        dsl.deleteFrom(UGC_SHORTS_PIPELINE_RUNS)
            .where(ID.eq(id))
            .execute() > 0

    private fun Record.toPipelineRun(): PipelineRun = PipelineRun(
        id = get(ID),
        workspaceId = get(WORKSPACE_ID),
        userId = get(USER_ID),
        sourceVideoId = get(SOURCE_VIDEO_ID),
        templateId = get(TEMPLATE_ID),
        autoSchedule = get(AUTO_SCHEDULE) == true,
        autoScheduleStartAt = localDateTime(AUTO_SCHEDULE_START_AT)?.atZone(ZoneOffset.UTC)?.toInstant(),
        autoScheduleIntervalHours = get(AUTO_SCHEDULE_INTERVAL_HOURS),
        autoSchedulePlatforms = get(AUTO_SCHEDULE_PLATFORMS)
            ?.toString()
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            .orEmpty(),
        idempotencyKey = get(IDEMPOTENCY_KEY),
        requestHash = get(REQUEST_HASH),
        status = PipelineRunStatus.valueOf(get(STATUS)),
        currentStage = get(CURRENT_STAGE)?.let { PipelineStage.valueOf(it) },
        transcriptText = get(TRANSCRIPT_TEXT),
        cropJson = get(CROP_JSON),
        clipCount = get(CLIP_COUNT),
        errorMessage = get(ERROR_MESSAGE),
        createdAt = localDateTime(CREATED_AT)!!.atZone(ZoneOffset.UTC).toInstant(),
        updatedAt = localDateTime(UPDATED_AT)!!.atZone(ZoneOffset.UTC).toInstant(),
        version = get(VERSION),
    )

    private fun localDateTime(value: java.time.Instant): LocalDateTime =
        LocalDateTime.ofInstant(value, ZoneOffset.UTC)

    private companion object {
        val REQUEST_HASH = DSL.field("request_hash", String::class.java)
    }
}
