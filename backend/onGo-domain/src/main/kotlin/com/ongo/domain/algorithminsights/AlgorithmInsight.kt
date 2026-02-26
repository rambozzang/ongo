package com.ongo.domain.algorithminsights

import java.time.LocalDateTime

data class AlgorithmInsight(
    val id: Long = 0,
    val workspaceId: Long,
    val platform: String,
    val factor: String,
    val importance: Int = 0,
    val currentScore: Int = 0,
    val recommendation: String? = null,
    val category: String,
    val trend: String = "STABLE",
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
