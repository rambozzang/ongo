package com.ongo.domain.algorithminsights

import java.time.LocalDateTime

data class AlgorithmChange(
    val id: Long = 0,
    val workspaceId: Long,
    val platform: String,
    val title: String,
    val description: String,
    val impact: String = "MEDIUM",
    val affectedAreas: List<String> = emptyList(),
    val detectedAt: LocalDateTime = LocalDateTime.now(),
    val recommendation: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
