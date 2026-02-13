package com.ongo.domain.abtest

import java.time.LocalDateTime

data class ABTest(
    val id: Long? = null,
    val userId: Long,
    val videoId: Long? = null,
    val testName: String,
    val status: String = "DRAFT",
    val metricType: String = "CTR",
    val winnerVariantId: Long? = null,
    val startedAt: LocalDateTime? = null,
    val endedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
)

data class ABTestVariant(
    val id: Long? = null,
    val testId: Long,
    val variantName: String,
    val title: String? = null,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val views: Long = 0,
    val clicks: Long = 0,
    val engagementRate: java.math.BigDecimal = java.math.BigDecimal.ZERO,
    val createdAt: LocalDateTime? = null,
)
