package com.ongo.domain.scheduleoptimizer

import java.time.LocalDateTime

data class ScheduleRecommendation(
    val id: Long? = null,
    val userId: Long,
    val videoId: Long,
    val videoTitle: String,
    val currentSchedule: LocalDateTime? = null,
    val recommendedSchedule: LocalDateTime,
    val platform: String,
    val expectedImprovement: Int = 0,
    val confidence: Int = 0,
    val status: String = "PENDING",
    val createdAt: LocalDateTime? = null,
)
