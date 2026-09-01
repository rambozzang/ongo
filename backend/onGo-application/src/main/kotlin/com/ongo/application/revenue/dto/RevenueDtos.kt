package com.ongo.application.revenue.dto

import java.time.LocalDate

/**
 * 수익 요약. **조회수는 여기 없다.**
 *
 * 그래서 이 응답만으로는 RPM(조회 1,000회당 수익)을 만들 수 없다. 예전 화면은 그 사실을
 * 우회하려고 `총수익 / (일수 × 10000)` 을 "평균 RPM" 으로 보여줬는데, `10000` 은 어디서도
 * 측정하지 않은 "하루 1만 조회" 가정이었다.
 *
 * 조회수 기반 단가가 필요하면 `/analytics/revenue/cpm-rpm`([CpmRpmResponse])을 쓸 것.
 * 그쪽은 `analytics_daily` 의 실제 조회수를 분모로 쓴다.
 */
data class RevenueSummaryResponse(
    val totalRevenue: Long,
    val totalRevenueKrw: Long,
    /**
     * 직전 동일 길이 기간 대비 성장률(%). **이전 기간 수익이 0 이면 `null`** 이다.
     *
     * 비율을 계산할 기준이 없다는 뜻이며, 소비자는 이것을 `0` 이나 `100` 으로 채우지 말고
     * "비교 불가"로 표시해야 한다. 예전에는 이 자리에 임의의 `100.0` 이 들어가, 첫 수익
     * 1,000 원과 100만 원이 똑같이 "+100%" 로 보였다.
     *
     * 대시보드 KPI 증감률과 같은 정책이다
     * ([com.ongo.domain.analytics.MetricChange] 참고).
     */
    val growthPercent: Double?,
    val platformBreakdown: List<PlatformRevenueItem>,
    /** 현재 연결된 플랫폼에서 광고 수익을 실제로 수집할 수 있는지. */
    val platformRevenueAvailable: Boolean = false,
    val platformRevenueUnavailableReason: String? = null,
    /** 금전 scope를 받기 위해 채널 재연동이 필요한지. */
    val platformRevenueReconnectRequired: Boolean = false,
)

data class PlatformRevenueItem(
    val platform: String,
    val revenueMicro: Long,
    val revenueKrw: Long,
    /**
     * 전체 수익에서 이 플랫폼이 차지하는 비율(%). **전체가 0 이면 `null`.**
     *
     * 비율은 분모가 0 이면 **정의되지 않는다**. 예전에는 그 자리에 `0.0` 을 넣어,
     * 수익이 아직 한 푼도 잡히지 않은 상태가 **"이 플랫폼 비중 0%"** 라는 관측처럼
     * 보였다. 그 값은 유료 AI 프롬프트에도 그대로 들어갔다.
     *
     * 분모가 양수이고 이 플랫폼 수익이 실제로 0 이면 그 `0.0` 은 관측이므로 유지한다.
     */
    val percentage: Double?,
)

data class RevenueTrendResponse(
    val data: List<RevenueTrendPoint>,
    val platformRevenueAvailable: Boolean = false,
    val platformRevenueUnavailableReason: String? = null,
    val platformRevenueReconnectRequired: Boolean = false,
)

data class RevenueTrendPoint(
    val date: LocalDate,
    val revenueMicro: Long,
    val revenueKrw: Long,
    val platform: String? = null,
)

data class PlatformRevenueResponse(
    val platforms: List<PlatformRevenueItem>,
    val platformRevenueAvailable: Boolean = false,
    val platformRevenueUnavailableReason: String? = null,
    val platformRevenueReconnectRequired: Boolean = false,
)
