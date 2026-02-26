package com.ongo.domain.platformhealth

import java.time.LocalDateTime

data class HealthIssue(
    val id: Long = 0,
    val healthScoreId: Long,
    val severity: String = "LOW",
    val category: String,
    val description: String,
    val recommendation: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
