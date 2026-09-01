package com.ongo.infrastructure.persistence.jooq

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import com.ongo.domain.ai.AiPipeline
import com.ongo.domain.ai.AiPipelineRepository
import com.ongo.domain.ai.AiPipelineStep
import com.ongo.domain.ai.PipelineCreditAllocation
import com.ongo.domain.ai.PipelineStatus
import com.ongo.domain.ai.PipelineStepStatus
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * AI pipeline state is deliberately stored as JSONB. AI results are polymorphic
 * per step, so forcing them into a relational DTO would either lose fields or
 * recreate a second, fragile schema for every provider response.
 */
@Repository
class AiPipelineJooqRepository(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
) : AiPipelineRepository {

    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    override fun findById(id: String): AiPipeline? =
        dsl.select()
            .from(TABLE)
            .where(ID.eq(id))
            .fetchOne()
            ?.toPipeline()

    override fun findActive(limit: Int): List<AiPipeline> =
        dsl.select()
            .from(TABLE)
            .where(STATUS.`in`(PipelineStatus.PENDING.name, PipelineStatus.RUNNING.name))
            .orderBy(UPDATED_AT.asc())
            .limit(limit.coerceIn(1, 200))
            .fetch()
            .map { it.toPipeline() }

    @Synchronized
    override fun claimForExecution(
        id: String,
        now: LocalDateTime,
        staleBefore: LocalDateTime,
    ): AiPipeline? {
        val affected = dsl.update(TABLE)
            .set(STATUS, PipelineStatus.RUNNING.name)
            .set(CURRENT_STEP, null as String?)
            .set(UPDATED_AT, now)
            .where(ID.eq(id))
            .and(
                STATUS.eq(PipelineStatus.PENDING.name)
                    .or(STATUS.eq(PipelineStatus.RUNNING.name).and(UPDATED_AT.lt(staleBefore)))
            )
            .execute()
        return if (affected == 1) findById(id) else null
    }

    /**
     * 아직 환불하지 않은 행만 정산 완료로 바꾼다.
     *
     * 조건을 WHERE 에 두어 DB 가 승자를 정한다. 읽고-판단하고-쓰면 실행 스레드의 자연 실패
     * 정산과 사용자의 취소가 모두 통과해 같은 금액을 두 번 돌려준다.
     *
     * `refunded_credits` 는 [save] 가 건드리지 않는다. 메모리 스냅샷의 0 이 표식을 지우면
     * 멱등이 무너지기 때문이다.
     */
    override fun settleRefund(
        id: String,
        refundedCredits: Int,
        status: PipelineStatus,
        completedAt: LocalDateTime,
    ): Boolean =
        dsl.update(TABLE)
            .set(REFUNDED_CREDITS, refundedCredits)
            .set(STATUS, status.name)
            .set(CURRENT_STEP, null as String?)
            .set(COMPLETED_AT, completedAt)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(id))
            .and(REFUNDED_CREDITS.eq(0))
            .execute() == 1

    /**
     * `refunded_credits` 를 **의도적으로 쓰지 않는다.** 이 메서드는 실행 중 상태를 자주
     * 덮어쓰는데, 메모리의 기본값 0 이 이미 확정된 환불 표식을 지우면 이중 환불이 열린다.
     * 그 컬럼은 [settleRefund] 만 바꾼다.
     */
    override fun save(pipeline: AiPipeline): AiPipeline {
        dsl.insertInto(TABLE)
            .set(ID, pipeline.id)
            .set(USER_ID, pipeline.userId)
            .set(VIDEO_ID, pipeline.videoId)
            .set(CHANNEL_ID, pipeline.channelId)
            .set(STEPS, json(pipeline.steps.map(AiPipelineStep::name)))
            .set(CURRENT_STEP, pipeline.currentStep?.name)
            .set(STATUS, pipeline.status.name)
            .set(STEP_STATUSES, json(pipeline.stepStatuses.mapKeys { it.key.name }))
            .set(RESULTS, json(pipeline.results.mapKeys { it.key.name }))
            .set(ERRORS, json(pipeline.errors.mapKeys { it.key.name }))
            .set(TOTAL_CREDITS_CHARGED, pipeline.totalCreditsCharged)
            // 차감 출처는 **생성 시점에 한 번만** 쓴다. 아래 doUpdate 에 넣지 않는 이유는
            // refunded_credits 와 같다 — 실행 중 잦은 저장이 스냅샷을 지우면 정산이
            // 출처를 잃고 fail-closed 로 떨어져 고객이 환불을 못 받는다.
            .set(CREDIT_ALLOCATION, pipeline.creditAllocation?.let { json(it) })
            .set(DISCOUNT_APPLIED, pipeline.discountApplied)
            .set(CREATED_AT, pipeline.createdAt)
            .set(UPDATED_AT, LocalDateTime.now())
            .set(COMPLETED_AT, pipeline.completedAt)
            .onConflict(ID)
            .doUpdate()
            .set(USER_ID, pipeline.userId)
            .set(VIDEO_ID, pipeline.videoId)
            .set(CHANNEL_ID, pipeline.channelId)
            .set(STEPS, json(pipeline.steps.map(AiPipelineStep::name)))
            .set(CURRENT_STEP, pipeline.currentStep?.name)
            .set(STATUS, pipeline.status.name)
            .set(STEP_STATUSES, json(pipeline.stepStatuses.mapKeys { it.key.name }))
            .set(RESULTS, json(pipeline.results.mapKeys { it.key.name }))
            .set(ERRORS, json(pipeline.errors.mapKeys { it.key.name }))
            .set(TOTAL_CREDITS_CHARGED, pipeline.totalCreditsCharged)
            .set(DISCOUNT_APPLIED, pipeline.discountApplied)
            .set(CREATED_AT, pipeline.createdAt)
            .set(UPDATED_AT, LocalDateTime.now())
            .set(COMPLETED_AT, pipeline.completedAt)
            .execute()
        return findById(pipeline.id) ?: error("AI 파이프라인 저장 후 조회할 수 없습니다: ${pipeline.id}")
    }

    private fun json(value: Any): JSONB = JSONB.jsonb(objectMapper.writeValueAsString(value))

    /**
     * 저장된 차감 분해를 읽는다. **읽지 못하면 `null`** — 없는 것과 같이 다룬다.
     *
     * 깨진 JSON 에 기본값을 채우면 "출처를 모른다"가 "무료분에서 0 을 썼다"로 둔갑해
     * 구매분이 조용히 사라진다. 파싱 실패는 fail-closed 로 넘겨 수기 정산 대상이 된다.
     */
    private fun readCreditAllocation(raw: Any?): PipelineCreditAllocation? {
        val json = when (raw) {
            null -> return null
            is JSONB -> raw.data()
            else -> raw.toString()
        }
        if (json.isBlank() || json == "null") return null
        return runCatching { objectMapper.readValue(json, PipelineCreditAllocation::class.java) }
            .onFailure { log.error("파이프라인 차감 분해를 읽지 못했다. 수기 정산 대상이다: {}", json, it) }
            .getOrNull()
    }

    private fun Record.toPipeline(): AiPipeline {
        val steps = readStringList(get(STEPS))
            .mapNotNull { runCatching { AiPipelineStep.valueOf(it) }.getOrNull() }
        val pipeline = AiPipeline(
            id = get(ID)!!,
            userId = get(USER_ID)!!,
            videoId = get(VIDEO_ID)!!,
            channelId = get(CHANNEL_ID),
            steps = steps,
            currentStep = get(CURRENT_STEP)?.let { runCatching { AiPipelineStep.valueOf(it) }.getOrNull() },
            status = runCatching { PipelineStatus.valueOf(get(STATUS)!!) }.getOrDefault(PipelineStatus.FAILED),
            totalCreditsCharged = get(TOTAL_CREDITS_CHARGED)!!,
            refundedCredits = get(REFUNDED_CREDITS) ?: 0,
            creditAllocation = readCreditAllocation(get(CREDIT_ALLOCATION)),
            discountApplied = get(DISCOUNT_APPLIED)!!,
            createdAt = timestamp(get(CREATED_AT))!!,
            completedAt = timestamp(get(COMPLETED_AT)),
        )

        pipeline.stepStatuses.clear()
        readObject(get(STEP_STATUSES)).forEach { (key, value) ->
            val step = runCatching { AiPipelineStep.valueOf(key) }.getOrNull()
            val status = runCatching { PipelineStepStatus.valueOf(value.asText()) }.getOrNull()
            if (step != null && status != null) pipeline.stepStatuses[step] = status
        }
        steps.forEach { pipeline.stepStatuses.putIfAbsent(it, PipelineStepStatus.PENDING) }

        readObject(get(RESULTS)).forEach { (key, value) ->
            val step = runCatching { AiPipelineStep.valueOf(key) }.getOrNull()
            if (step != null && !value.isNull) pipeline.results[step] = value
        }
        readObject(get(ERRORS)).forEach { (key, value) ->
            val step = runCatching { AiPipelineStep.valueOf(key) }.getOrNull()
            if (step != null && !value.isNull) pipeline.errors[step] = value.asText()
        }
        return pipeline
    }

    private fun readStringList(raw: Any?): List<String> =
        runCatching {
            objectMapper.readValue(rawJson(raw), object : TypeReference<List<String>>() {})
        }.getOrDefault(emptyList())

    private fun readObject(raw: Any?): Map<String, JsonNode> =
        runCatching {
            objectMapper.readValue(rawJson(raw), object : TypeReference<Map<String, JsonNode>>() {})
        }.getOrDefault(emptyMap())

    private fun rawJson(raw: Any?): String = when (raw) {
        is JSONB -> raw.data()
        null -> "{}"
        else -> raw.toString()
    }

    private fun timestamp(raw: Any?): LocalDateTime? = when (raw) {
        is LocalDateTime -> raw
        is java.sql.Timestamp -> raw.toLocalDateTime()
        else -> null
    }

    companion object {
        private val TABLE = DSL.table(DSL.name("ai_pipeline_jobs"))
        private val ID = DSL.field(DSL.name("id"), String::class.java)
        private val USER_ID = DSL.field(DSL.name("user_id"), Long::class.java)
        private val VIDEO_ID = DSL.field(DSL.name("video_id"), Long::class.java)
        private val CHANNEL_ID = DSL.field(DSL.name("channel_id"), Long::class.java)
        private val STEPS = DSL.field(DSL.name("steps"), JSONB::class.java)
        private val CURRENT_STEP = DSL.field(DSL.name("current_step"), String::class.java)
        private val STATUS = DSL.field(DSL.name("status"), String::class.java)
        private val STEP_STATUSES = DSL.field(DSL.name("step_statuses"), JSONB::class.java)
        private val RESULTS = DSL.field(DSL.name("results"), JSONB::class.java)
        private val CREDIT_ALLOCATION = DSL.field(DSL.name("credit_allocation"), JSONB::class.java)
        private val ERRORS = DSL.field(DSL.name("errors"), JSONB::class.java)
        private val TOTAL_CREDITS_CHARGED = DSL.field(DSL.name("total_credits_charged"), Int::class.java)
        private val REFUNDED_CREDITS = DSL.field(DSL.name("refunded_credits"), Int::class.java)
        private val DISCOUNT_APPLIED = DSL.field(DSL.name("discount_applied"), Boolean::class.java)
        private val CREATED_AT = DSL.field(DSL.name("created_at"), LocalDateTime::class.java)
        private val UPDATED_AT = DSL.field(DSL.name("updated_at"), LocalDateTime::class.java)
        private val COMPLETED_AT = DSL.field(DSL.name("completed_at"), LocalDateTime::class.java)
    }
}
