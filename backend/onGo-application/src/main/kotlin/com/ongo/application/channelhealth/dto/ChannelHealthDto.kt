package com.ongo.application.channelhealth.dto

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class ChannelHealthMetricResponse(
    val id: Long,
    val channelId: Long,
    val channelName: String,
    val platform: String,
    val overallScore: Int,
    val growthScore: Int,
    val engagementScore: Int,
    val consistencyScore: Int,
    val audienceScore: Int,
    val monetizationScore: Int,
    val measuredAt: LocalDateTime?,
)

data class HealthTrendResponse(
    val id: Long,
    val metricId: Long,
    val category: String,
    val date: LocalDate,
    val score: Int,
    val change: BigDecimal,
    val recommendation: String?,
)

data class ChannelHealthSummaryResponse(
    val totalChannels: Int,
    val avgOverallScore: Int,
    val topChannel: String,
    val weakestArea: String,
    val trendsUp: Int,
    val trendsDown: Int,
)
