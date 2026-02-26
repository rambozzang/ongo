package com.ongo.application.brandsafety.dto

data class BrandSafetyCheckResponse(
    val id: Long,
    val contentTitle: String,
    val contentType: String,
    val platform: String,
    val status: String,
    val overallScore: Int,
    val checks: List<SafetyCheckItemResponse>,
    val checkedAt: String?,
    val createdAt: String,
)

data class SafetyCheckItemResponse(
    val id: Long,
    val category: String,
    val name: String,
    val status: String,
    val severity: String,
    val description: String,
    val recommendation: String?,
)

data class BrandSafetyRuleResponse(
    val id: Long,
    val name: String,
    val category: String,
    val description: String,
    val isEnabled: Boolean,
    val severity: String,
    val createdAt: String,
)

data class RunSafetyCheckRequest(
    val contentId: Long,
    val contentTitle: String,
    val contentType: String,
    val platform: String,
)

data class ToggleRuleRequest(
    val isEnabled: Boolean,
)

data class BrandSafetySummaryResponse(
    val totalChecks: Int,
    val safeCount: Int,
    val warningCount: Int,
    val violationCount: Int,
    val avgScore: Double,
)
