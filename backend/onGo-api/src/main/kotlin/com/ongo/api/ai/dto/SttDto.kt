package com.ongo.api.ai.dto

import jakarta.validation.constraints.Positive

data class SttRequest(
    @field:Positive(message = "영상 ID는 0보다 커야 합니다")
    val videoId: Long,
)

data class SttResponse(
    val text: String,
    val segments: List<SttSegment>,
) {
    data class SttSegment(
        val startTime: Double,
        val endTime: Double,
        val text: String,
    )
}
