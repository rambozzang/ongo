package com.ongo.domain.revenuegoal

import java.time.LocalDate
import java.time.LocalDateTime

data class RevenueGoal(
    val id: Long = 0,
    val workspaceId: Long,
    val name: String,
    val targetAmount: Long,
    val currentAmount: Long = 0,
    val currency: String = "KRW",
    val period: String,
    val platform: String = "ALL",
    val status: String = "ACTIVE",
    val progress: Int = 0,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
