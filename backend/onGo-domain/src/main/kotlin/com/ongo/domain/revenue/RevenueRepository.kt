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

/**
 * 플랫폼별 수익 측정 상태 집계.
 *
 * 금액만으로는 "0 원을 벌었다" 와 "물어보지 못했다" 가 구분되지 않는다. 화면이 무엇을
 * 안내할지는 이 집계가 정한다.
 */
data class PlatformRevenueStatusCount(
    val platform: String,
    val status: String,
    val rows: Long,
)

interface RevenueRepository {
    fun getDailyRevenue(userId: Long, from: LocalDate, to: LocalDate): List<DailyRevenue>
    fun getPlatformRevenue(userId: Long, from: LocalDate, to: LocalDate): List<PlatformRevenue>
    fun getTotalRevenue(userId: Long, from: LocalDate, to: LocalDate): Long

    /**
     * 기간 내 플랫폼 × 측정상태별 행 수.
     *
     * 위 금액 조회들은 `MEASURED` 행만 더한다. 그래서 "0 원" 응답이 미측정 때문인지
     * 실제로 0 원이어서인지는 이 집계로만 알 수 있다.
     */
    fun getRevenueStatusCounts(userId: Long, from: LocalDate, to: LocalDate): List<PlatformRevenueStatusCount>
    fun getPaymentTotal(userId: Long, from: LocalDate, to: LocalDate): Long
    fun getCpmRpmByPlatform(userId: Long, from: LocalDate, to: LocalDate): List<CpmRpmRaw>
    fun getBrandDealRevenue(userId: Long, from: LocalDate, to: LocalDate): List<BrandDealRevenueRaw>
}
