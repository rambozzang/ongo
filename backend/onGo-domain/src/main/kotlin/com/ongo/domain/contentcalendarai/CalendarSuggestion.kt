package com.ongo.domain.contentcalendarai

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class CalendarSuggestion(
    val id: Long = 0,
    val workspaceId: Long,
    val title: String,
    val description: String? = null,
    val suggestedDate: LocalDate,
    val suggestedTime: String,
    val platform: String,
    val contentType: String,
    val topic: String? = null,
    val expectedEngagement: BigDecimal = BigDecimal.ZERO,
    val confidence: Int = 0,
    val status: String = "PENDING",
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
