package com.ongo.domain.platformhealth

import java.time.LocalDateTime

data class PlatformHealthScore(
    val id: Long = 0,
    val workspaceId: Long,
    val platform: String,
    val overallScore: Int = 0,
    val growthScore: Int = 0,
    val engagementScore: Int = 0,
    val consistencyScore: Int = 0,
    val audienceScore: Int = 0,
    val trend: String = "STABLE",
    val checkedAt: LocalDateTime = LocalDateTime.now(),
)
