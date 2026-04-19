package com.ongo.domain.revenue

import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.LocalTime

data class RevenueAlertConfig(
    val id: Long? = null,
    val userId: Long,
    val alertType: String,
    val thresholdValue: BigDecimal? = null,
    val scheduleTime: LocalTime? = null,
    val isEnabled: Boolean = true,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
