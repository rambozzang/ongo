package com.ongo.domain.schedule

import com.ongo.common.enums.ScheduleStatus
import java.time.LocalDateTime

data class Schedule(
    val id: Long? = null,
    val videoId: Long,
    val userId: Long,
    val scheduledAt: LocalDateTime,
    val status: ScheduleStatus = ScheduleStatus.SCHEDULED,
    val platforms: Map<String, Any?> = emptyMap(),
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
