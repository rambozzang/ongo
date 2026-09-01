package com.ongo.application.revenue.dto

data class CpmRpmResponse(
    val platforms: List<CpmRpmItem>,
    val platformRevenueAvailable: Boolean = false,
    val platformRevenueUnavailableReason: String? = null,
    val platformRevenueReconnectRequired: Boolean = false,
)

/**
 * 플랫폼별 단가. **분모가 없으면 단가는 `null`** 이다.
 *
 * CPM 은 노출 1,000회당, RPM 은 조회 1,000회당 수익이다. 노출이나 조회가 0 이면
 * 나눌 것이 없어 단가라는 개념 자체가 성립하지 않는다. 예전에는 그 자리에 `0.0` 을
 * 넣어 화면이 **"CPM ₩0.00"** 을 그렸다 — 재지 않았을 뿐인데 "수익성이 0" 이라는
 * 관측이 된다.
 *
 * 반대로 **분모가 양수인데 수익이 0 이면 그 0 은 실측이다.** 노출은 났는데 수익이
 * 붙지 않았다는 뜻이므로 그대로 보존한다.
 */
data class CpmRpmItem(
    val platform: String,
    /** 노출 1,000회당 수익. 노출이 0 이면 `null`. */
    val cpm: Double?,
    /** 조회 1,000회당 수익. 조회가 0 이면 `null`. */
    val rpm: Double?,
    val impressions: Long,
    val views: Long,
    val revenueMicro: Long,
    /**
     * 계산할 수 없었던 단가와 그 이유. 키는 `"cpm"` / `"rpm"`.
     *
     * 단가 필드와 **같은 자리에서 함께 만든다.** 목록을 따로 두면 `cpm = null` 인데
     * 이유가 비어 있는 상태가 생기고, 화면은 이유 없이 빈 칸만 그리게 된다.
     */
    val unavailableMetrics: Map<String, String> = emptyMap(),
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
