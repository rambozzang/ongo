package com.ongo.application.revenue.dto

import java.math.BigDecimal
import java.time.LocalTime

data class RevenueAlertConfigResponse(
    val id: Long,
    val alertType: String,
    val thresholdValue: BigDecimal?,
    val scheduleTime: LocalTime?,
    val isEnabled: Boolean,
)

data class SaveRevenueAlertConfigRequest(
    val alertType: String,
    val thresholdValue: BigDecimal? = null,
    val scheduleTime: LocalTime? = null,
    val isEnabled: Boolean = true,
)

data class RevenueAlertConfigListResponse(
    val configs: List<RevenueAlertConfigResponse>,
)
