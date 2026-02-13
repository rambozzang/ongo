package com.ongo.application.recurring.dto

import java.time.LocalDateTime
import java.time.LocalTime

data class RecurringScheduleResponse(
    val id: Long,
    val name: String,
    val frequency: String,
    val dayOfWeek: Int?,
    val dayOfMonth: Int?,
    val timeOfDay: LocalTime,
    val timezone: String,
    val platforms: List<String>,
    val titleTemplate: String?,
    val descriptionTemplate: String?,
    val tags: List<String>,
    val isActive: Boolean,
    val nextRunAt: LocalDateTime?,
    val lastRunAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

data class CreateRecurringScheduleRequest(
    val name: String,
    val frequency: String,
    val dayOfWeek: Int? = null,
    val dayOfMonth: Int? = null,
    val timeOfDay: String, // HH:mm format
    val timezone: String = "Asia/Seoul",
    val platforms: List<String> = emptyList(),
    val titleTemplate: String? = null,
    val descriptionTemplate: String? = null,
    val tags: List<String> = emptyList(),
    val isActive: Boolean = true,
)

data class UpdateRecurringScheduleRequest(
    val name: String? = null,
    val frequency: String? = null,
    val dayOfWeek: Int? = null,
    val dayOfMonth: Int? = null,
    val timeOfDay: String? = null,
    val timezone: String? = null,
    val platforms: List<String>? = null,
    val titleTemplate: String? = null,
    val descriptionTemplate: String? = null,
    val tags: List<String>? = null,
)
