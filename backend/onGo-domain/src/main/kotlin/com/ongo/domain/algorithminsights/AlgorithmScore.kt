package com.ongo.domain.algorithminsights

import java.time.LocalDateTime

data class AlgorithmScore(
    val id: Long = 0,
    val workspaceId: Long,
    val platform: String,
    val overallScore: Int = 0,
    val contentScore: Int = 0,
    val engagementScore: Int = 0,
    val metadataScore: Int = 0,
    val consistencyScore: Int = 0,
    val audienceScore: Int = 0,
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
