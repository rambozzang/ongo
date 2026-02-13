package com.ongo.api.ai.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class GenerateReportRequest(
    @field:Min(value = 1, message = "분석 기간은 최소 1일입니다")
    @field:Max(value = 90, message = "분석 기간은 최대 90일입니다")
    val days: Int = 7,
)

data class GenerateReportResponse(
    val reportMarkdown: String,
    val highlights: List<String>,
    val improvements: List<String>,
    val nextWeekSuggestions: List<String>,
)
