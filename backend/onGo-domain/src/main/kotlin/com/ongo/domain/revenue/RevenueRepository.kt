package com.ongo.domain.revenue

import java.time.LocalDate

data class DailyRevenue(
    val date: LocalDate,
    val revenueMicro: Long,
    val platform: String? = null,
)

data class PlatformRevenue(
    val platform: String,
    val totalRevenueMicro: Long,
)

data class CpmRpmRaw(
    val platform: String,
    val impressions: Long,
    val views: Long,
    val revenueMicro: Long,
)

data class BrandDealRevenueRaw(
    val id: Long,
    val brandName: String,
    val dealValue: Long,
    val status: String,
    val platform: String?,
)

interface RevenueRepository {
    fun getDailyRevenue(userId: Long, from: LocalDate, to: LocalDate): List<DailyRevenue>
    fun getPlatformRevenue(userId: Long, from: LocalDate, to: LocalDate): List<PlatformRevenue>
    fun getTotalRevenue(userId: Long, from: LocalDate, to: LocalDate): Long
    fun getPaymentTotal(userId: Long, from: LocalDate, to: LocalDate): Long
    fun getCpmRpmByPlatform(userId: Long, from: LocalDate, to: LocalDate): List<CpmRpmRaw>
    fun getBrandDealRevenue(userId: Long, from: LocalDate, to: LocalDate): List<BrandDealRevenueRaw>
}
