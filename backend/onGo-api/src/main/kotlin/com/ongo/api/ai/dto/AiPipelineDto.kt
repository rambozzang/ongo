package com.ongo.api.ai.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive

data class AiPipelineRequest(
    @field:Positive(message = "영상 ID는 0보다 커야 합니다")
    val videoId: Long,
    @field:NotEmpty(message = "파이프라인 스텝을 1개 이상 선택해주세요")
    val steps: List<String>,
    val channelId: Long? = null,
)

data class AiPipelineResponse(
    val pipelineId: String,
    val videoId: Long,
    val steps: List<PipelineStepStatusDto>,
    val totalCredits: Int,
    val discountApplied: Boolean,
    val status: String,
    val results: Map<String, Any?>,
)

data class PipelineStepStatusDto(
    val step: String,
    val displayName: String,
    val creditCost: Int,
    val status: String,
    val result: Any? = null,
    val error: String? = null,
)
