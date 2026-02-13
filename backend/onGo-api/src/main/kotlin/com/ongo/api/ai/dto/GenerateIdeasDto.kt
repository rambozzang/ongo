package com.ongo.api.ai.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class GenerateIdeasRequest(
    @field:NotBlank(message = "카테고리는 필수입니다")
    @field:Size(max = 100, message = "카테고리는 최대 100자까지 입력할 수 있습니다")
    val category: String,
    @field:Size(max = 20, message = "최근 영상 제목은 최대 20개까지 입력할 수 있습니다")
    val recentTitles: List<String> = emptyList(),
)

data class GenerateIdeasResponse(
    val ideas: List<ContentIdea>,
) {
    data class ContentIdea(
        val title: String,
        val description: String,
        val expectedReaction: String,
        val difficulty: String,
    )
}
