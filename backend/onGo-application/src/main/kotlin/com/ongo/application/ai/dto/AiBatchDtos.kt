package com.ongo.application.ai.dto

import com.ongo.common.enums.Platform

data class AiBatchRequest(
    val videoIds: List<Long>,
    val operation: AiBatchOperation,
    val platform: Platform? = null,
)

enum class AiBatchOperation {
    GENERATE_META,
    GENERATE_HASHTAGS,
    STT,
    ALL,
}

data class AiBatchResponse(
    val batchId: String,
    val totalItems: Int,
    val status: BatchStatus,
    val items: List<AiBatchItemStatus>,
)

enum class BatchStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    PARTIALLY_FAILED,
}

data class AiBatchItemStatus(
    val videoId: Long,
    val videoTitle: String?,
    val status: ItemStatus,
    val result: Any? = null,
    val error: String? = null,
)

enum class ItemStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
}
