package com.ongo.domain.usage

import java.time.LocalDateTime

data class UsageAlertConfig(
    val id: Long? = null,
    val userId: Long,
    val alertType: String,
    val thresholdPercent: Int = 80,
    val enabled: Boolean = true,
    val lastAlertedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
)
