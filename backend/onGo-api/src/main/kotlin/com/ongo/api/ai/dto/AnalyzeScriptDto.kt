package com.ongo.api.ai.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AnalyzeScriptRequest(
    @field:NotBlank(message = "스크립트는 필수입니다")
    @field:Size(max = 10000, message = "스크립트는 최대 10000자까지 입력할 수 있습니다")
    val script: String,
)

data class AnalyzeScriptResponse(
    val keywords: List<String>,
    val targetAudience: String,
    val suggestedCategory: String,
    val summary: String,
)
