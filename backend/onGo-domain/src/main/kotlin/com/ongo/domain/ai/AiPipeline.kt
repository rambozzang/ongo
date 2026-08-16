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
    val channelId: Long? = null,
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

/**
 * AI 파이프라인은 크레딧을 차감한 뒤 실행되므로 메모리 맵을 진실의 원천으로
 * 삼을 수 없다. 구현체는 이 상태를 DB에 저장하고 재시작 뒤 active job을 복구한다.
 */
interface AiPipelineRepository {
    fun findById(id: String): AiPipeline?
    fun findActive(limit: Int): List<AiPipeline>
    /** PENDING 또는 오래된 RUNNING 하나만 원자적으로 실행 상태로 선점한다. */
    fun claimForExecution(id: String, now: java.time.LocalDateTime, staleBefore: java.time.LocalDateTime): AiPipeline?
    fun save(pipeline: AiPipeline): AiPipeline
}
