package com.ongo.domain.aicalendar

import java.time.LocalDate
import java.time.LocalDateTime

data class AiContentCalendar(
    val id: Long? = null,
    val userId: Long,
    val title: String? = null,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val settings: String? = null, // JSONB as String
    val calendarData: String, // JSONB as String
    val status: String = "DRAFT",
    val createdAt: LocalDateTime? = null,
)
