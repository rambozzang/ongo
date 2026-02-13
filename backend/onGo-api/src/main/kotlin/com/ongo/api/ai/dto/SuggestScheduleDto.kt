package com.ongo.api.ai.dto

import jakarta.validation.constraints.Positive

data class SuggestScheduleRequest(
    @field:Positive(message = "채널 ID는 0보다 커야 합니다")
    val channelId: Long,
)

data class SuggestScheduleResponse(
    val suggestions: List<ScheduleSuggestion>,
) {
    data class ScheduleSuggestion(
        val dayOfWeek: String,
        val time: String,
        val reason: String,
        val expectedBoost: Double,
    )
}
