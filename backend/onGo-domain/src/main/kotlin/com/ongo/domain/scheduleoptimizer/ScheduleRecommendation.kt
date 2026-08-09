package com.ongo.domain.scheduleoptimizer

import java.time.LocalDateTime

data class ScheduleRecommendation(
    val id: Long? = null,
    val userId: Long,
    val videoId: Long,
    /** Connected channel account. Null is retained for legacy recommendations. */
    val channelId: Long? = null,
    val videoTitle: String,
    val currentSchedule: LocalDateTime? = null,
    val recommendedSchedule: LocalDateTime,
    val platform: String,
    val expectedImprovement: Int = 0,
    val confidence: Int = 0,
    val status: String = "PENDING",
    val createdAt: LocalDateTime? = null,
)
