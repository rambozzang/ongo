package com.ongo.application.ai

import com.ongo.application.ai.dto.AiBatchOperation
import com.ongo.application.ai.dto.AiBatchRequest
import com.ongo.application.ai.dto.AiBatchResponse
import java.time.LocalDateTime

/** Durable port for credit-consuming batch jobs. */
interface AiBatchRepository {
    fun findById(batchId: String): PersistedAiBatch?
    fun findActive(limit: Int): List<PersistedAiBatch>
    fun claimForExecution(
        batchId: String,
        now: LocalDateTime,
        staleBefore: LocalDateTime,
    ): PersistedAiBatch?
    fun save(batch: AiBatchResponse, request: AiBatchRequest): PersistedAiBatch
    fun update(batch: AiBatchResponse): PersistedAiBatch
    /** Atomically changes one item so parallel workers cannot overwrite each other. */
    fun updateItem(
        batchId: String,
        index: Int,
        status: com.ongo.application.ai.dto.ItemStatus,
        result: Any? = null,
        error: String? = null,
    ): PersistedAiBatch?
}

data class PersistedAiBatch(
    val response: AiBatchResponse,
    val videoIds: List<Long>,
    val operation: AiBatchOperation,
    val platform: com.ongo.common.enums.Platform?,
)
