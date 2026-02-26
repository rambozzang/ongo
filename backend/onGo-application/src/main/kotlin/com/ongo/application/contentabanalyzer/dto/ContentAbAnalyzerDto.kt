package com.ongo.application.contentabanalyzer.dto

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class ContentVariantResponse(
    val id: Long,
    val label: String,
    val videoId: Long,
    val videoTitle: String,
    val views: Long,
    val likes: Long,
    val comments: Long,
    val ctr: BigDecimal,
    val avgWatchTime: Int,
)

data class ContentAbTestResponse(
    val id: Long,
    val title: String,
    val variantA: ContentVariantResponse?,
    val variantB: ContentVariantResponse?,
    val status: String,
    val winner: String?,
    val confidence: BigDecimal,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val createdAt: LocalDateTime?,
)

data class ContentAbSummaryResponse(
    val totalTests: Int,
    val completedTests: Int,
    val avgConfidence: Double,
    val winRateA: Double,
    val winRateB: Double,
)

data class CreateAbTestRequest(
    val title: String,
    val videoIdA: Long,
    val videoIdB: Long,
)
