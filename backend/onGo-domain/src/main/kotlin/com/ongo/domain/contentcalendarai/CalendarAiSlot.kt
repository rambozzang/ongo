package com.ongo.domain.contentcalendarai

import java.time.LocalDate
import java.time.LocalDateTime

data class CalendarAiSlot(
    val id: Long = 0,
    val workspaceId: Long,
    val slotDate: LocalDate,
    val slotTime: String,
    val platform: String,
    val score: Int = 0,
    val reason: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
