package com.ongo.domain.event

data class ScheduleFailedEvent(
    val scheduleId: Long,
    val videoId: Long,
    val userId: Long,
    val reason: String,
)
