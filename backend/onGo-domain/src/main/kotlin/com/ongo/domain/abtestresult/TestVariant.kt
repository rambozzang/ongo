package com.ongo.domain.abtestresult

import java.math.BigDecimal
import java.time.LocalDateTime

data class TestVariant(
    val id: Long = 0,
    val resultId: Long,
    val variantId: String,
    val name: String,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val views: Long = 0,
    val clicks: Long = 0,
    val ctr: BigDecimal = BigDecimal.ZERO,
    val avgWatchTime: Int = 0,
    val engagement: BigDecimal = BigDecimal.ZERO,
    val conversions: Int = 0,
    val isControl: Boolean = false,
    val isWinner: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
