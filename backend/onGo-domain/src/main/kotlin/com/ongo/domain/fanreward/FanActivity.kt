package com.ongo.domain.fanreward

import java.time.LocalDateTime

data class FanActivity(
    val id: Long? = null,
    val userId: Long,
    val fanName: String,
    val activityType: String,
    val points: Int = 0,
    val description: String? = null,
    val timestamp: LocalDateTime? = null,
)
