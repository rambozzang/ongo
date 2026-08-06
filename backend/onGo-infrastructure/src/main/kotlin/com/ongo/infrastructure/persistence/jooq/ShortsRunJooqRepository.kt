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
import com.ongo.infrastructure.persistence.jooq.Fields.SOURCE_VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.TEMPLATE_ID
import com.ongo.infrastructure.persistence.jooq.Fields.CROP_JSON
import com.ongo.infrastructure.persistence.jooq.Fields.TRANSCRIPT_TEXT
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VERSION
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_SHORTS_PIPELINE_RUNS
import org.jooq.DSLContext
import org.jooq.Record
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

    override fun update(run: PipelineRun): PipelineRun {
        // 낙관적 락: 로드 시점 version과 일치할 때만 갱신하고 version을 증가시킨다.
        val affected = dsl.update(UGC_SHORTS_PIPELINE_RUNS)
            .set(TEMPLATE_ID, run.templateId)
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
}
