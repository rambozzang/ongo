package com.ongo.application.revenue.dto

data class CpmRpmResponse(
    val platforms: List<CpmRpmItem>,
)

data class CpmRpmItem(
    val platform: String,
    val cpm: Double,
    val rpm: Double,
    val impressions: Long,
    val views: Long,
    val revenueMicro: Long,
)

data class BrandDealRevenueResponse(
    val deals: List<BrandDealRevenueItem>,
    val totalRevenue: Long,
    val totalRevenueKrw: Long,
)

data class BrandDealRevenueItem(
    val id: Long,
    val brandName: String,
    val dealValue: Long,
    val dealValueKrw: Long,
    val status: String,
    val platform: String?,
)
