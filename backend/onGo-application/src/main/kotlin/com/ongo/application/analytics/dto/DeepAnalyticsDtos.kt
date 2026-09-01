package com.ongo.application.analytics.dto

/**
 * 트래픽 소스 분포. **[available] 은 [sources] 에 항목이 있을 때만 `true`** 다.
 *
 * 판정은 저장된 행 수가 아니라 **관측 항목의 존재**로 한다. `channel_insights_daily`
 * 행이 있어도 `traffic_source` 가 `{}` 면 유입을 잰 적이 없기 때문이다. 그래서 빈
 * [sources] 와 `total = 0` 은 "유입이 0 건이었다" 는 관측이 **아니다**. 두 상태는 같은
 * 모양으로 나가면 안 된다.
 *
 * ## 지금은 사실상 항상 닫혀 있다
 *
 * `channel_insights_daily` 를 채우는
 * [com.ongo.domain.analytics.AnalyticsRepository.upsertChannelInsights] 는 저장소 구현만
 * 있고 **호출부가 하나도 없다**. 어댑터가 돌려주는
 * [com.ongo.infrastructure.external.platform.PlatformAnalytics] 에도 트래픽 소스 필드가
 * 없어서, 지금 코드로는 그 테이블이 채워질 방법 자체가 없다
 * (`ChannelInsightsAvailabilityTest` 가 그 사실을 고정한다).
 *
 * 다만 그것은 **현재 상태**이지 계약이 아니다. 수집이 붙으면 이 응답은 코드 수정 없이
 * 열린다 — 판정이 데이터에서 나오기 때문이다.
 */
data class TrafficSourceResponse(
    val period: String,
    val sources: Map<String, Long>,
    val total: Long,
    /**
     * 이 지표를 실제로 관측했는가. **판정 기준은 [sources] 의 항목 존재**다.
     *
     * 저장된 행이 있어도 `traffic_source` 가 `{}` 면 잰 적이 없다 — 행 수로 판정하면
     * 빈 분포가 "유입 0 건" 이라는 관측으로 보인다. 반대로 `SEARCH -> 0` 은 **관측**이라
     * 항목이 있으므로 `true` 다. 빈 맵과 `0` 관측은 다른 상태다.
     *
     * **기존 필드는 그대로 두고 추가한 값**이라, 이 필드를 모르는 기존 클라이언트는
     * 예전과 똑같이 동작한다(빈 분포를 받는다).
     */
    val available: Boolean = false,
    /** [available] 가 `false` 일 때의 사유. 측정된 경우 `null`. */
    val unavailableReason: String? = null,
)

/**
 * 시청자 인구통계. **[available] 은 세 분포 중 하나라도 항목이 있을 때 `true`** 다.
 *
 * 판정 근거는 [TrafficSourceResponse] 와 같다 — 같은 `channel_insights_daily` 를 읽고,
 * 행 수가 아니라 관측 항목의 존재로 가른다. 빈 분포는 "그런 시청자가 없었다" 가 아니라
 * **재지 않았다** 는 뜻이다.
 *
 * 부분 관측도 관측이다. 연령만 잡히면 열리고, 비어 있는 성별·국가는 **빈 맵 그대로**
 * 남는다 — 없는 항목을 지어내지 않는다.
 *
 * 수집 경로가 아직 없다는 **현재 상태**는 [TrafficSourceResponse] 설명을 참고.
 */
data class DemographicsResponse(
    val period: String,
    val ageDistribution: Map<String, Double>,
    val genderDistribution: Map<String, Double>,
    val topCountries: Map<String, Long>,
    /**
     * 이 지표를 실제로 관측했는가. **세 분포 중 하나라도 항목이 있으면 `true`** 다.
     *
     * 부분 관측도 관측이다 — 예를 들어 연령만 있고 성별·국가가 비어 있으면 `true` 이고,
     * 비어 있는 분포는 **빈 맵 그대로** 남는다(없는 항목을 지어내지 않는다).
     *
     * 판정은 값이 아니라 키로 한다. `"25-34" -> 0.0` 은 관측이므로 `true` 다.
     * 기존 필드를 바꾸지 않고 추가한 값이다.
     */
    val available: Boolean = false,
    /** [available] 가 `false` 일 때의 사유. 측정된 경우 `null`. */
    val unavailableReason: String? = null,
)

/**
 * 하루치 CTR. **이 포인트가 존재한다는 것 자체가 그날 노출이 측정됐다는 뜻**이다.
 *
 * 측정된 행이 없는 날짜는 포인트를 만들지 않는다 — 0 포인트를 그리면 그날 클릭률이
 * 0 이었다는 관측이 된다.
 */
data class CTRTrendPoint(
    val date: String,
    val impressions: Long,
    /** [impressions] 와 **같은 행**에서 나온 조회수. 다른 플랫폼 조회수를 섞지 않는다. */
    val views: Long,
    /**
     * 클릭률(%). 이 포인트는 `impressions > 0` 인 행으로만 만들어지므로 실제로는 항상
     * 값이 있다. nullable 인 것은 [CTRResponse.avgCTR] 과 계약을 맞추기 위해서다.
     */
    val ctr: Double?,
)

/**
 * CTR 추세.
 *
 * ## `null` 은 "재지 않았다" 이며 0 이 아니다
 *
 * 노출(`impressions`)을 조회하는 어댑터는 YouTube 하나뿐이다
 * (`YouTubeClient.kt:149` 의 metrics 목록). 나머지 플랫폼만 쓰는 크리에이터에게는 분모가
 * 없으므로 클릭률이 존재하지 않는다. 예전에는 그 자리에 0 을 넣어 화면이
 * **"평균 CTR 0% · 총 노출 0"** 을 성과처럼 보여줬다.
 *
 * 측정된 행이 있을 때의 0% 는 관측 결과이므로 그대로 `0.0` 이다.
 */
data class CTRResponse(
    val period: String,
    /** 측정된 노출 대비 조회수 비율(%). **측정 가능한 행이 없으면 `null`.** */
    val avgCTR: Double?,
    /** 측정된 노출 합. **없으면 `null`** — 0 은 "노출이 0회였다" 는 주장이 된다. */
    val totalImpressions: Long?,
    /** 날짜별 포인트. **측정된 행이 있는 날짜만** 담는다. */
    val data: List<CTRTrendPoint>,
    /**
     * 이 합계가 어느 플랫폼의 표본인지.
     *
     * 합계만 보면 모집단을 알 수 없다 — 전 플랫폼인지 YouTube 뿐인지 구분돼야 한다.
     */
    val measuredPlatforms: List<String> = emptyList(),
    /** [avgCTR] 이 `null` 인 이유. 화면이 그대로 보여줄 수 있는 문장. */
    val unavailableReason: String? = null,
)

/**
 * 평균 시청 시간 추세.
 *
 * ## `null` 은 "재지 않았다" 이며 0 이 아니다
 *
 * 시청 시간(`estimatedMinutesWatched`)을 조회하는 어댑터는 YouTube 하나뿐이다. 다른
 * 플랫폼만 쓰는 크리에이터에게는 분자가 없으므로 평균이 존재하지 않는다. 예전에는 그
 * 자리에 0 이 들어가 화면이 **"0초"** 를 관측 결과처럼 보여줬다.
 *
 * 유효한 행이 있을 때의 0초는 관측 결과이므로 그대로 `0` 이다.
 */
data class AvgViewDurationResponse(
    val period: String,
    /** 조회수로 가중된 평균 시청 시간(초). **유효한 행이 없으면 `null`.** */
    val avgDurationSeconds: Long?,
    /** 날짜별 포인트. **조회가 측정된 날짜만** 담는다. */
    val data: List<AvgViewDurationPoint>,
    /** 이 평균이 어느 플랫폼의 표본인지. 합계만 보면 모집단을 알 수 없다. */
    val measuredPlatforms: List<String> = emptyList(),
    /** [avgDurationSeconds] 가 `null` 인 이유. 화면이 그대로 보여줄 수 있는 문장. */
    val unavailableReason: String? = null,
)

/**
 * 하루치 평균 시청 시간. **이 포인트가 존재한다는 것 자체가 그날 조회가 측정됐다는 뜻**이다.
 */
data class AvgViewDurationPoint(
    val date: String,
    val avgDurationSeconds: Long,
    /** [totalViews] 와 **같은 행**에서 나온 시청 시간. 다른 플랫폼 값을 섞지 않는다. */
    val totalWatchTimeSeconds: Long,
    val totalViews: Long,
)

/**
 * 구독 전환 추세.
 *
 * ## `null` 은 "재지 않았다" 이며 0 이 아니다
 *
 * 구독 증가(`subscribersGained`)를 조회하는 어댑터는 YouTube 하나뿐이다. 다른 플랫폼만
 * 쓰는 크리에이터에게는 분자가 없으므로 전환율이 존재하지 않는다. 예전에는 그 자리에 0 을
 * 넣어 화면이 `총 신규 구독 **+0**` 을 초록색으로 보여줬다.
 *
 * 유효한 행이 있을 때의 0 은 관측 결과이므로 그대로 `0` 이다.
 */
data class SubscriberConversionResponse(
    val period: String,
    /** 측정된 구독 증가 합. **유효한 행이 없으면 `null`** — 0 은 성과 주장이 된다. */
    val totalGained: Long?,
    /** 날짜별 포인트. **조회가 측정된 날짜만** 담는다. */
    val data: List<SubscriberConversionPoint>,
    /** 이 합계가 어느 플랫폼의 표본인지. 합계만 보면 모집단을 알 수 없다. */
    val measuredPlatforms: List<String> = emptyList(),
    /** [totalGained] 가 `null` 인 이유. 화면이 그대로 보여줄 수 있는 문장. */
    val unavailableReason: String? = null,
)

/**
 * 하루치 구독 전환. **이 포인트가 존재한다는 것 자체가 그날 조회가 측정됐다는 뜻**이다.
 */
data class SubscriberConversionPoint(
    val date: String,
    /** [views] 와 **같은 행**에서 나온 구독 증가 수. 다른 플랫폼 값을 섞지 않는다. */
    val gained: Int,
    val views: Long,
    /**
     * 전환율(%). 이 포인트는 `views > 0` 인 행으로만 만들어지므로 실제로는 항상 값이 있다.
     * nullable 인 것은 [SubscriberConversionResponse] 와 계약을 맞추기 위해서다.
     */
    val conversionRate: Double?,
)
