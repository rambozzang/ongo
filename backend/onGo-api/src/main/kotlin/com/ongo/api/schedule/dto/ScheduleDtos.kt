package com.ongo.api.schedule.dto

import com.ongo.common.enums.Platform
import com.ongo.common.enums.ScheduleStatus
import java.time.LocalDateTime

data class CreateScheduleRequest(
    val videoId: Long,
    val scheduledAt: LocalDateTime,
    val platforms: List<PlatformScheduleConfig>
)

data class PlatformScheduleConfig(
    val platform: Platform,
    val scheduledAt: LocalDateTime? = null
)

data class UpdateScheduleRequest(
    val scheduledAt: LocalDateTime? = null,
    val platforms: List<PlatformScheduleConfig>? = null
)

data class ScheduleResponse(
    val id: Long,
    val videoId: Long,
    val videoTitle: String?,
    val thumbnailUrl: String?,
    val scheduledAt: LocalDateTime,
    val status: ScheduleStatus,
    val platforms: List<PlatformScheduleConfig>,
    val createdAt: LocalDateTime?
)

data class ScheduleCalendarResponse(
    val schedules: List<ScheduleResponse>,
    val from: LocalDateTime,
    val to: LocalDateTime
)
