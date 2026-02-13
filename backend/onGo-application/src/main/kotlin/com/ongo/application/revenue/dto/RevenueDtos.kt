package com.ongo.application.revenue.dto

import java.time.LocalDate

data class RevenueSummaryResponse(
    val totalRevenue: Long,
    val totalRevenueKrw: Long,
    val growthPercent: Double,
    val platformBreakdown: List<PlatformRevenueItem>,
)

data class PlatformRevenueItem(
    val platform: String,
    val revenueMicro: Long,
    val revenueKrw: Long,
    val percentage: Double,
)

data class RevenueTrendResponse(
    val data: List<RevenueTrendPoint>,
)

data class RevenueTrendPoint(
    val date: LocalDate,
    val revenueMicro: Long,
    val revenueKrw: Long,
    val platform: String? = null,
)

data class PlatformRevenueResponse(
    val platforms: List<PlatformRevenueItem>,
)
