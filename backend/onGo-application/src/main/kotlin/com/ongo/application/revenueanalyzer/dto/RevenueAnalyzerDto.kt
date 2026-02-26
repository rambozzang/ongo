package com.ongo.application.revenueanalyzer.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class RevenueStreamResponse(
    val id: Long,
    val channelId: Long,
    val channelName: String,
    val source: String,
    val amount: BigDecimal,
    val currency: String,
    val period: String,
    val growth: BigDecimal,
    val createdAt: LocalDateTime?,
)

data class RevenueProjectionResponse(
    val id: Long,
    val channelId: Long,
    val source: String,
    val currentMonthly: BigDecimal,
    val projectedMonthly: BigDecimal,
    val confidence: Int,
    val projectionDate: String,
    val factors: List<String>,
    val createdAt: LocalDateTime?,
)

data class RevenueAnalyzerSummaryResponse(
    val totalRevenue: BigDecimal,
    val monthlyGrowth: BigDecimal,
    val topSource: String,
    val channelCount: Int,
    val projectedNextMonth: BigDecimal,
    val avgConfidence: Int,
)
