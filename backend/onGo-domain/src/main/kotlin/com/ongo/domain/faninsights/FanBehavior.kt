package com.ongo.domain.faninsights

import java.math.BigDecimal
import java.time.LocalDateTime

data class FanBehavior(
    val id: Long = 0,
    val workspaceId: Long,
    val platform: String,
    val activeHour: Int = 0,
    val activeDay: String? = null,
    val watchDuration: BigDecimal = BigDecimal.ZERO,
    val returnRate: BigDecimal = BigDecimal.ZERO,
    val commentRate: BigDecimal = BigDecimal.ZERO,
    val shareRate: BigDecimal = BigDecimal.ZERO,
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
