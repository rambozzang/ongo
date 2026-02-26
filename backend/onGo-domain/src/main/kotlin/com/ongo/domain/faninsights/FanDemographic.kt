package com.ongo.domain.faninsights

import java.math.BigDecimal
import java.time.LocalDateTime

data class FanDemographic(
    val id: Long = 0,
    val workspaceId: Long,
    val platform: String,
    val ageGroup: String,
    val gender: String,
    val percentage: BigDecimal = BigDecimal.ZERO,
    val country: String? = null,
    val city: String? = null,
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
