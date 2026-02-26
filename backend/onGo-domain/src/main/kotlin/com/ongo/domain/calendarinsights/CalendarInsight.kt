package com.ongo.domain.calendarinsights

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class CalendarInsight(
    val id: Long = 0,
    val workspaceId: Long,
    val date: LocalDate,
    val dayOfWeek: String,
    val hour: Int,
    val uploadCount: Int = 0,
    val avgViews: Long = 0,
    val avgEngagement: BigDecimal = BigDecimal.ZERO,
    val score: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
