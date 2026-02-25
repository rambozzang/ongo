package com.ongo.application.aicalendar.dto

import java.time.LocalDate
import java.time.LocalDateTime

data class AiCalendarResponse(
    val id: Long,
    val title: String?,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val settings: String?,
    val calendarData: String,
    val status: String,
    val createdAt: LocalDateTime?,
)

data class GenerateCalendarRequest(
    val title: String? = null,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val settings: String? = null,
)

data class UpdateCalendarRequest(
    val title: String? = null,
    val calendarData: String? = null,
    val status: String? = null,
)
