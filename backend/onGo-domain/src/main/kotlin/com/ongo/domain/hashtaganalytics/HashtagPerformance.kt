package com.ongo.domain.hashtaganalytics

import java.math.BigDecimal
import java.time.LocalDateTime

data class HashtagPerformance(
    val id: Long = 0,
    val workspaceId: Long,
    val hashtag: String,
    val platform: String,
    val usageCount: Int = 0,
    val totalViews: Long = 0,
    val avgEngagement: BigDecimal = BigDecimal.ZERO,
    val growthRate: BigDecimal = BigDecimal.ZERO,
    val trendDirection: String = "STABLE",
    val category: String? = null,
    val lastUsedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
