package com.ongo.application.platformhealth.dto

data class PlatformHealthScoreResponse(
    val id: Long,
    val platform: String,
    val overallScore: Int,
    val growthScore: Int,
    val engagementScore: Int,
    val consistencyScore: Int,
    val audienceScore: Int,
    val trend: String,
    val checkedAt: String,
)

data class HealthIssueResponse(
    val id: Long,
    val healthScoreId: Long,
    val severity: String,
    val category: String,
    val description: String,
    val recommendation: String?,
)

data class PlatformHealthSummaryResponse(
    val totalPlatforms: Int,
    val avgHealthScore: Double,
    val healthiestPlatform: String,
    val criticalIssues: Int,
    val overallTrend: String,
)
