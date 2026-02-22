package com.ongo.api.ai.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class RevenueReportRequest(
    @field:Min(value = 1, message = "분석 기간은 최소 1일입니다")
    @field:Max(value = 90, message = "분석 기간은 최대 90일입니다")
    val days: Int = 30,
)

data class RevenueReportResponse(
    val reportMarkdown: String,
    val highlights: List<String>,
    val improvements: List<String>,
    val optimizationTips: List<String>,
    val platformBreakdown: List<PlatformRevenueBreakdown>,
) {
    data class PlatformRevenueBreakdown(
        val platform: String,
        val contribution: String,
        val trend: String,
        val suggestion: String,
    )
}
