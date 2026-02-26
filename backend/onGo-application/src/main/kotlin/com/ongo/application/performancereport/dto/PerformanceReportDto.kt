package com.ongo.application.performancereport.dto

import java.time.LocalDate
import java.time.LocalDateTime

data class PerformanceReportResponse(
    val id: Long,
    val title: String,
    val period: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val status: String,
    val totalViews: Long,
    val totalEngagement: Long,
    val topVideoId: Long?,
    val topVideoTitle: String?,
    val reportUrl: String?,
    val createdAt: LocalDateTime?,
)

data class ReportSectionResponse(
    val id: Long,
    val reportId: Long,
    val sectionType: String,
    val title: String,
    val content: String,
    val chartData: String?,
    val sortOrder: Int,
)

data class PerformanceReportSummaryResponse(
    val totalReports: Int,
    val scheduledReports: Int,
    val avgViewsPerPeriod: Long,
    val bestPeriod: String,
    val growthRate: Double,
)

data class GenerateReportRequest(
    val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
)
