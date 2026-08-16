package com.ongo.infrastructure.persistence.jooq

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import com.ongo.domain.ai.AiPipeline
import com.ongo.domain.ai.AiPipelineRepository
import com.ongo.domain.ai.AiPipelineStep
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
        private val ERRORS = DSL.field(DSL.name("errors"), JSONB::class.java)
        private val TOTAL_CREDITS_CHARGED = DSL.field(DSL.name("total_credits_charged"), Int::class.java)
        private val DISCOUNT_APPLIED = DSL.field(DSL.name("discount_applied"), Boolean::class.java)
        private val CREATED_AT = DSL.field(DSL.name("created_at"), LocalDateTime::class.java)
        private val UPDATED_AT = DSL.field(DSL.name("updated_at"), LocalDateTime::class.java)
        private val COMPLETED_AT = DSL.field(DSL.name("completed_at"), LocalDateTime::class.java)
    }
}
