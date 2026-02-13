package com.ongo.domain.ai

import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

enum class PipelineStatus {
    PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
}

enum class PipelineStepStatus {
    PENDING, RUNNING, COMPLETED, FAILED, SKIPPED
}

data class AiPipeline(
    val id: String,
    val userId: Long,
    val videoId: Long,
    val steps: List<AiPipelineStep>,
    var currentStep: AiPipelineStep? = null,
    var status: PipelineStatus = PipelineStatus.PENDING,
    val stepStatuses: MutableMap<AiPipelineStep, PipelineStepStatus> = ConcurrentHashMap(),
    val results: MutableMap<AiPipelineStep, Any?> = ConcurrentHashMap(),
    val errors: MutableMap<AiPipelineStep, String?> = ConcurrentHashMap(),
    val totalCreditsCharged: Int = 0,
    val discountApplied: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var completedAt: LocalDateTime? = null,
) {
    init {
        steps.forEach { stepStatuses[it] = PipelineStepStatus.PENDING }
    }
}
