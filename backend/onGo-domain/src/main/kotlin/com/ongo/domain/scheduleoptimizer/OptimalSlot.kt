package com.ongo.domain.scheduleoptimizer

import java.time.LocalDateTime

data class OptimalSlot(
    val id: Long? = null,
    val userId: Long,
    val platform: String,
    val dayOfWeek: String,
    val hour: Int,
    val score: Int = 0,
    val audienceOnline: Int = 0,
    val competitionLevel: String = "MEDIUM",
    val reason: String,
    val createdAt: LocalDateTime? = null,
)
