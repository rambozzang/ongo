package com.ongo.domain.trend

import java.time.LocalDateTime

data class TrendAlert(
    val id: Long? = null,
    val userId: Long,
    val keyword: String,
    val threshold: Double = 50.0,
    val enabled: Boolean = true,
    val createdAt: LocalDateTime? = null,
)
