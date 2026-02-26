package com.ongo.domain.performancereport

import java.time.LocalDate
import java.time.LocalDateTime

data class PerformanceReport(
    val id: Long? = null,
    val userId: Long,
    val title: String,
    val period: String = "MONTHLY",
    val startDate: LocalDate,
    val endDate: LocalDate,
    val status: String = "DRAFT",
    val totalViews: Long = 0,
    val totalEngagement: Long = 0,
    val topVideoId: Long? = null,
    val topVideoTitle: String? = null,
    val reportUrl: String? = null,
    val createdAt: LocalDateTime? = null,
)
