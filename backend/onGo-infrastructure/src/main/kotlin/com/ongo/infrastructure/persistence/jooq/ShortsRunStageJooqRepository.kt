package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.shorts.PipelineStage
import com.ongo.domain.ugc.shorts.RunStage
import com.ongo.domain.ugc.shorts.RunStageRepository
import com.ongo.domain.ugc.shorts.RunStageStatus
import com.ongo.infrastructure.persistence.jooq.Fields.AI_PROVIDER
import com.ongo.infrastructure.persistence.jooq.Fields.COMPLETED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.CREDIT_COST
import com.ongo.infrastructure.persistence.jooq.Fields.ERROR_MESSAGE
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PROMPT_ID
import com.ongo.infrastructure.persistence.jooq.Fields.PROMPT_REVISION
import com.ongo.infrastructure.persistence.jooq.Fields.RUN_ID
import com.ongo.infrastructure.persistence.jooq.Fields.STAGE
import com.ongo.infrastructure.persistence.jooq.Fields.STARTED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_SHORTS_RUN_STAGES
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Repository
class ShortsRunStageJooqRepository(
    private val dsl: DSLContext,
) : RunStageRepository {

    override fun save(stage: RunStage): RunStage {
        val id = dsl.insertInto(UGC_SHORTS_RUN_STAGES)
            .set(RUN_ID, stage.runId)
            .set(STAGE, stage.stage.name)
            .set(STATUS, stage.status.name)
            .set(PROMPT_ID, stage.promptId)
            .set(PROMPT_REVISION, stage.promptRevision)
            .set(AI_PROVIDER, stage.aiProvider)
            .set(CREDIT_COST, stage.creditCost)
            .set(INPUT_SNAPSHOT_JSONB, stage.inputSnapshot?.let { JSONB.jsonb(it) })
            .set(OUTPUT_SNAPSHOT_JSONB, stage.outputSnapshot?.let { JSONB.jsonb(it) })
            .set(ERROR_MESSAGE, stage.errorMessage)
            .set(STARTED_AT, stage.startedAt?.toLocalDateTime())
            .set(COMPLETED_AT, stage.completedAt?.toLocalDateTime())
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(stage: RunStage): RunStage {
        dsl.update(UGC_SHORTS_RUN_STAGES)
            .set(STATUS, stage.status.name)
            .set(PROMPT_ID, stage.promptId)
            .set(PROMPT_REVISION, stage.promptRevision)
            .set(AI_PROVIDER, stage.aiProvider)
            .set(CREDIT_COST, stage.creditCost)
            .set(INPUT_SNAPSHOT_JSONB, stage.inputSnapshot?.let { JSONB.jsonb(it) })
            .set(OUTPUT_SNAPSHOT_JSONB, stage.outputSnapshot?.let { JSONB.jsonb(it) })
            .set(ERROR_MESSAGE, stage.errorMessage)
            .set(STARTED_AT, stage.startedAt?.toLocalDateTime())
            .set(COMPLETED_AT, stage.completedAt?.toLocalDateTime())
            .where(ID.eq(stage.id))
            .execute()

        return findById(stage.id)!!
    }

    override fun findByRunId(runId: Long): List<RunStage> =
        dsl.select()
            .from(UGC_SHORTS_RUN_STAGES)
            .where(RUN_ID.eq(runId))
            .orderBy(ID.asc())
            .fetch()
            .map { it.toRunStage() }

    override fun findByRunIdAndStage(runId: Long, stage: PipelineStage): RunStage? =
        dsl.select()
            .from(UGC_SHORTS_RUN_STAGES)
            .where(RUN_ID.eq(runId))
            .and(STAGE.eq(stage.name))
            .fetchOne()
            ?.toRunStage()

    override fun deleteFrom(runId: Long, fromSortOrder: Int): Int {
        // stage가 문자열 컬럼이라 sortOrder 이상 단계명 목록으로 IN 조건을 만든다.
        val stageNames = PipelineStage.entries
            .filter { it.sortOrder >= fromSortOrder }
            .map { it.name }
        return dsl.deleteFrom(UGC_SHORTS_RUN_STAGES)
            .where(RUN_ID.eq(runId))
            .and(STAGE.`in`(stageNames))
            .execute()
    }

    private fun findById(id: Long): RunStage? =
        dsl.select()
            .from(UGC_SHORTS_RUN_STAGES)
            .where(ID.eq(id))
            .fetchOne()
            ?.toRunStage()

    private fun Record.toRunStage(): RunStage = RunStage(
        id = get(ID),
        runId = get(RUN_ID),
        stage = PipelineStage.valueOf(get(STAGE)),
        status = RunStageStatus.valueOf(get(STATUS)),
        promptId = get(PROMPT_ID),
        promptRevision = get(PROMPT_REVISION),
        aiProvider = get(AI_PROVIDER),
        creditCost = get(CREDIT_COST),
        inputSnapshot = jsonbString("input_snapshot"),
        outputSnapshot = jsonbString("output_snapshot"),
        errorMessage = get(ERROR_MESSAGE),
        startedAt = localDateTime(STARTED_AT)?.atZone(ZoneOffset.UTC)?.toInstant(),
        completedAt = localDateTime(COMPLETED_AT)?.atZone(ZoneOffset.UTC)?.toInstant(),
    )

    private fun Instant.toLocalDateTime(): LocalDateTime =
        LocalDateTime.ofInstant(this, ZoneOffset.UTC)

    companion object {
        // input/output_snapshot은 JSONB 컬럼이라 String 바인딩으로 쓸 수 없어 JSONB 타입 필드로만 쓴다.
        private val INPUT_SNAPSHOT_JSONB = DSL.field("input_snapshot", JSONB::class.java)
        private val OUTPUT_SNAPSHOT_JSONB = DSL.field("output_snapshot", JSONB::class.java)

        /** jsonb 컬럼 값을 JSON 문자열로 꺼낸다. 드라이버 반환 타입(JSONB/PGobject/String)을 모두 수용한다. */
        private fun Record.jsonbString(column: String): String? = when (val raw = get(column)) {
            null -> null
            is JSONB -> raw.data()
            is String -> raw
            else -> raw.toString()
        }
    }
}
