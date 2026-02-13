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

interface RevenueRepository {
    fun getDailyRevenue(userId: Long, from: LocalDate, to: LocalDate): List<DailyRevenue>
    fun getPlatformRevenue(userId: Long, from: LocalDate, to: LocalDate): List<PlatformRevenue>
    fun getTotalRevenue(userId: Long, from: LocalDate, to: LocalDate): Long
    fun getPaymentTotal(userId: Long, from: LocalDate, to: LocalDate): Long
}
