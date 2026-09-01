package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.shorts.PipelineStage
import com.ongo.domain.ugc.shorts.RunStage
import com.ongo.domain.ugc.shorts.RunStageRepository
import com.ongo.domain.ugc.shorts.RunStageStatus
import com.ongo.domain.ai.PipelineCreditAllocation
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
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

    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    override fun save(stage: RunStage): RunStage {
        val id = dsl.insertInto(UGC_SHORTS_RUN_STAGES)
            .set(RUN_ID, stage.runId)
            .set(STAGE, stage.stage.name)
            .set(STATUS, stage.status.name)
            .set(PROMPT_ID, stage.promptId)
            .set(PROMPT_REVISION, stage.promptRevision)
            .set(AI_PROVIDER, stage.aiProvider)
            .set(CREDIT_COST, stage.creditCost)
            // 차감 분해는 **저장 시점에만** 쓴다. 프로세스를 넘어서는 환불의 유일한 근거다.
            .set(CREDIT_ALLOCATION, stage.creditAllocation?.let { json(it) })
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

    /*
     * `credit_allocation` 과 `refunded_credits` 를 **의도적으로 쓰지 않는다.**
     *
     * 이 메서드는 실행 중 상태를 자주 덮어쓰는데, 메모리의 기본값(null·0)이 확정된 분해나
     * 정산 표식을 지우면 환불 근거가 사라지거나 이중 환불이 열린다. 분해는 [save] 가,
     * 표식은 [settleRefund] 만 쓴다.
     */
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

    override fun findUnsettled(runId: Long, fromSortOrder: Int): List<RunStage> {
        val stageNames = PipelineStage.entries
            .filter { it.sortOrder >= fromSortOrder }
            .map { it.name }
        return dsl.select()
            .from(UGC_SHORTS_RUN_STAGES)
            .where(RUN_ID.eq(runId))
            .and(STAGE.`in`(stageNames))
            // 완료된 단계는 일한 대가로 정당하게 청구된 것이라 환불 대상이 아니다.
            .and(STATUS.eq(RunStageStatus.RUNNING.name))
            .and(REFUNDED_CREDITS.eq(0))
            .and(CREDIT_COST.gt(0))
            .fetch()
            .map { it.toRunStage() }
    }

    /*
     * 조건을 SQL 에 둔다. 읽고-판단하고-쓰면 동시에 들어온 두 정산이 모두 통과해 두 번
     * 환불된다. 계약은 인터페이스에 있다.
     *
     * 단계를 FAILED 로 함께 닫는다. 닫지 않으면 findUnsettled 가 같은 행을 영원히 다시 집는다.
     */
    override fun settleRefund(stageId: Long, refundedCredits: Int, reason: String): Boolean =
        dsl.update(UGC_SHORTS_RUN_STAGES)
            .set(REFUNDED_CREDITS, refundedCredits)
            .set(STATUS, RunStageStatus.FAILED.name)
            .set(ERROR_MESSAGE, reason)
            .set(COMPLETED_AT, LocalDateTime.now())
            .where(ID.eq(stageId))
            .and(STATUS.eq(RunStageStatus.RUNNING.name))
            .and(REFUNDED_CREDITS.eq(0))
            .execute() > 0

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
        creditAllocation = readCreditAllocation(get(CREDIT_ALLOCATION)),
        refundedCredits = get(REFUNDED_CREDITS) ?: 0,
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
        private val CREDIT_ALLOCATION = DSL.field(DSL.name("credit_allocation"), JSONB::class.java)
        private val REFUNDED_CREDITS = DSL.field(DSL.name("refunded_credits"), Int::class.java)
        private val OUTPUT_SNAPSHOT_JSONB = DSL.field("output_snapshot", JSONB::class.java)

        /** jsonb 컬럼 값을 JSON 문자열로 꺼낸다. 드라이버 반환 타입(JSONB/PGobject/String)을 모두 수용한다. */
        private fun Record.jsonbString(column: String): String? = when (val raw = get(column)) {
            null -> null
            is JSONB -> raw.data()
            is String -> raw
            else -> raw.toString()
        }
    }

    private fun json(value: Any): JSONB = JSONB.jsonb(objectMapper.writeValueAsString(value))

    /**
     * 저장된 분해를 읽는다. 읽지 못하면 `null` — **자동 환불하지 않고** 수기 정산으로 넘긴다.
     * 깨진 값을 추측해 돌려주는 것이 이 컬럼이 막으려는 손실이다.
     */
    private fun readCreditAllocation(raw: Any?): PipelineCreditAllocation? {
        val json = when (raw) {
            null -> return null
            is JSONB -> raw.data()
            else -> raw.toString()
        }
        if (json.isBlank() || json == "null") return null
        return runCatching { objectMapper.readValue(json, PipelineCreditAllocation::class.java) }
            .onFailure { log.error("쇼츠 단계 차감 분해를 읽지 못했다. 수기 정산 대상이다: {}", json, it) }
            .getOrNull()
    }

}
