package com.ongo.application.multibrand.dto

import java.time.LocalDateTime

data class CreateBrandRequest(
    val name: String,
    val color: String = "BLUE",
    val category: String? = null,
    val assignedEditors: List<String> = emptyList(),
)

data class UpdateBrandRequest(
    val name: String? = null,
    val color: String? = null,
    val category: String? = null,
    val assignedEditors: List<String>? = null,
    val isActive: Boolean? = null,
)

data class BrandResponse(
    val id: Long,
    val name: String,
    val logoUrl: String?,
    val color: String,
    val category: String?,
    val assignedEditors: List<String>,
    val totalScheduled: Int,
    val totalPublished: Int,
    val isActive: Boolean,
    val createdAt: LocalDateTime?,
)

data class CreateScheduleRequest(
    val brandId: Long,
    val title: String,
    val platform: String,
    val scheduledAt: LocalDateTime,
    val assignedTo: String? = null,
    val notes: String? = null,
)

data class UpdateScheduleRequest(
    val title: String? = null,
    val platform: String? = null,
    val scheduledAt: LocalDateTime? = null,
    val status: String? = null,
    val assignedTo: String? = null,
    val notes: String? = null,
)

data class BrandScheduleItemResponse(
    val id: Long,
    val brandId: Long,
    val brandName: String,
    val brandColor: String,
    val title: String,
    val platform: String,
    val scheduledAt: LocalDateTime,
    val status: String,
    val assignedTo: String?,
    val videoId: String?,
    val notes: String?,
)

data class CalendarConflictResponse(
    val date: String,
    val items: List<BrandScheduleItemResponse>,
    val severity: String,
    val message: String,
)

data class BrandPerformanceReportResponse(
    val brandId: Long,
    val brandName: String,
    val period: String,
    val totalUploads: Int,
    val totalViews: Long,
    val totalEngagement: Long,
    val avgCtr: Double,
    val revenueGenerated: Long,
    val topContent: List<TopContentItem>,
)

data class TopContentItem(
    val title: String,
    val views: Long,
    val platform: String,
)

data class MultiBrandSummaryResponse(
    val totalBrands: Int,
    val activeBrands: Int,
    val totalScheduledThisWeek: Int,
    val totalPublishedThisMonth: Int,
    val conflicts: List<CalendarConflictResponse>,
    val brandPerformances: List<BrandPerformanceReportResponse>,
)
