package com.ongo.domain.growthcoach

import java.time.LocalDateTime

data class GrowthGoal(
    val id: Long? = null,
    val userId: Long,
    val type: String,
    val targetValue: Long,
    val currentValue: Long = 0,
    val deadline: String,
    val progress: Int = 0,
    val createdAt: LocalDateTime? = null,
)
