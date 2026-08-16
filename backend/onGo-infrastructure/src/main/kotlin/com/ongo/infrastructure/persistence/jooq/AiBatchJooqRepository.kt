package com.ongo.infrastructure.persistence.jooq

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.ai.AiBatchRepository
import com.ongo.application.ai.PersistedAiBatch
import com.ongo.application.ai.dto.AiBatchItemStatus
import com.ongo.application.ai.dto.AiBatchOperation
import com.ongo.application.ai.dto.AiBatchRequest
import com.ongo.application.ai.dto.AiBatchResponse
import com.ongo.application.ai.dto.BatchStatus
import com.ongo.application.ai.dto.ItemStatus
import com.ongo.common.enums.Platform
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Repository
class AiBatchJooqRepository(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
) : AiBatchRepository {

    override fun findById(batchId: String): PersistedAiBatch? =
        dsl.select().from(TABLE).where(ID.eq(batchId)).fetchOne()?.toBatch()

    override fun findActive(limit: Int): List<PersistedAiBatch> =
        dsl.select()
            .from(TABLE)
            .where(STATUS.`in`(BatchStatus.PENDING.name, BatchStatus.PROCESSING.name))
            .orderBy(UPDATED_AT.asc())
            .limit(limit.coerceIn(1, 200))
            .fetch()
            .map { it.toBatch() }

    override fun claimForExecution(
        batchId: String,
        now: LocalDateTime,
        staleBefore: LocalDateTime,
    ): PersistedAiBatch? {
        val affected = dsl.update(TABLE)
            .set(STATUS, BatchStatus.PROCESSING.name)
            .set(UPDATED_AT, now)
            .where(ID.eq(batchId))
            .and(
                STATUS.eq(BatchStatus.PENDING.name)
                    .or(STATUS.eq(BatchStatus.PROCESSING.name).and(UPDATED_AT.lt(staleBefore)))
            )
            .execute()
        return if (affected == 1) findById(batchId) else null
    }

    override fun save(batch: AiBatchResponse, request: AiBatchRequest): PersistedAiBatch {
        dsl.insertInto(TABLE)
            .set(ID, batch.batchId)
            .set(USER_ID, batch.userId)
            .set(OPERATION, request.operation.name)
            .set(PLATFORM, request.platform?.name)
            .set(VIDEO_IDS, json(request.videoIds))
            .set(ITEMS, json(batch.items))
            .set(TOTAL_ITEMS, batch.totalItems)
            .set(STATUS, batch.status.name)
            .set(CREATED_AT, LocalDateTime.now())
            .set(UPDATED_AT, LocalDateTime.now())
            .onConflict(ID)
            .doUpdate()
            .set(USER_ID, batch.userId)
            .set(OPERATION, request.operation.name)
            .set(PLATFORM, request.platform?.name)
            .set(VIDEO_IDS, json(request.videoIds))
            .set(ITEMS, json(batch.items))
            .set(TOTAL_ITEMS, batch.totalItems)
            .set(STATUS, batch.status.name)
            .set(UPDATED_AT, LocalDateTime.now())
            .execute()
        return findById(batch.batchId) ?: error("AI batch 저장 후 조회할 수 없습니다: ${batch.batchId}")
    }

    override fun update(batch: AiBatchResponse): PersistedAiBatch {
        val current = findById(batch.batchId) ?: error("AI batch를 찾을 수 없습니다: ${batch.batchId}")
        return save(
            batch,
            AiBatchRequest(
                videoIds = current.videoIds,
                operation = current.operation,
                platform = current.platform,
            ),
        )
    }

    @Transactional
    override fun updateItem(
        batchId: String,
        index: Int,
        status: ItemStatus,
        result: Any?,
        error: String?,
    ): PersistedAiBatch? {
        val current = dsl.select()
            .from(TABLE)
            .where(ID.eq(batchId))
            .forUpdate()
            .fetchOne()
            ?.toBatch() ?: return null
        if (index !in current.response.items.indices) return current

        val items = current.response.items.toMutableList()
        items[index] = items[index].copy(status = status, result = result, error = error)
        return save(current.response.copy(items = items), current.request())
    }

    private fun json(value: Any): JSONB = JSONB.jsonb(objectMapper.writeValueAsString(value))

    private fun Record.toBatch(): PersistedAiBatch {
        val response = AiBatchResponse(
            batchId = get(ID)!!,
            userId = get(USER_ID)!!,
            totalItems = get(TOTAL_ITEMS)!!,
            status = runCatching { BatchStatus.valueOf(get(STATUS)!!) }.getOrDefault(BatchStatus.PARTIALLY_FAILED),
            items = readItems(get(ITEMS)),
        )
        return PersistedAiBatch(
            response = response,
            videoIds = readLongs(get(VIDEO_IDS)),
            operation = runCatching { AiBatchOperation.valueOf(get(OPERATION)!!) }.getOrDefault(AiBatchOperation.ALL),
            platform = get(PLATFORM)?.let { runCatching { Platform.valueOf(it) }.getOrNull() },
        )
    }

    private fun PersistedAiBatch.request() = AiBatchRequest(videoIds, operation, platform)

    private fun readItems(raw: Any?): List<AiBatchItemStatus> =
        runCatching {
            objectMapper.readValue(rawJson(raw), object : TypeReference<List<AiBatchItemStatus>>() {})
        }.getOrDefault(emptyList())

    private fun readLongs(raw: Any?): List<Long> =
        runCatching {
            objectMapper.readValue(rawJson(raw), object : TypeReference<List<Long>>() {})
        }.getOrDefault(emptyList())

    private fun rawJson(raw: Any?): String = when (raw) {
        is JSONB -> raw.data()
        null -> "[]"
        else -> raw.toString()
    }

    companion object {
        private val TABLE = DSL.table(DSL.name("ai_batch_jobs"))
        private val ID = DSL.field(DSL.name("id"), String::class.java)
        private val USER_ID = DSL.field(DSL.name("user_id"), Long::class.java)
        private val OPERATION = DSL.field(DSL.name("operation"), String::class.java)
        private val PLATFORM = DSL.field(DSL.name("platform"), String::class.java)
        private val VIDEO_IDS = DSL.field(DSL.name("video_ids"), JSONB::class.java)
        private val ITEMS = DSL.field(DSL.name("items"), JSONB::class.java)
        private val TOTAL_ITEMS = DSL.field(DSL.name("total_items"), Int::class.java)
        private val STATUS = DSL.field(DSL.name("status"), String::class.java)
        private val CREATED_AT = DSL.field(DSL.name("created_at"), LocalDateTime::class.java)
        private val UPDATED_AT = DSL.field(DSL.name("updated_at"), LocalDateTime::class.java)
    }
}
