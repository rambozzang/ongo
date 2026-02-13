package com.ongo.domain.recurring

import java.time.LocalDateTime
import java.time.LocalTime

data class RecurringSchedule(
    val id: Long? = null,
    val userId: Long,
    val name: String,
    val frequency: String,
    val dayOfWeek: Int? = null,
    val dayOfMonth: Int? = null,
    val timeOfDay: LocalTime,
    val timezone: String = "Asia/Seoul",
    val platforms: List<String> = emptyList(),
    val titleTemplate: String? = null,
    val descriptionTemplate: String? = null,
    val tags: List<String> = emptyList(),
    val isActive: Boolean = true,
    val nextRunAt: LocalDateTime? = null,
    val lastRunAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
