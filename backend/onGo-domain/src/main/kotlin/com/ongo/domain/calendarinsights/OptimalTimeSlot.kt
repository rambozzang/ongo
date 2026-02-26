package com.ongo.domain.calendarinsights

import java.math.BigDecimal
import java.time.LocalDateTime

data class OptimalTimeSlot(
    val id: Long = 0,
    val workspaceId: Long,
    val platform: String? = null,
    val dayOfWeek: String,
    val hour: Int,
    val score: Int = 0,
    val expectedViews: Long = 0,
    val expectedEngagement: BigDecimal = BigDecimal.ZERO,
    val reason: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
