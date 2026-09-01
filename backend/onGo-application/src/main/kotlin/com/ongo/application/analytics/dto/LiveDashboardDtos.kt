package com.ongo.application.analytics.dto

/**
 * 라이브 대시보드 전체 상태 응답.
 * 프론트엔드 LiveDashboardState 타입과 1:1 매핑.
 */
data class LiveDashboardStateResponse(
    val metrics: List<LiveMetricResponse>,
    val alerts: List<LiveAlertResponse>,
    val activePlatforms: List<String>,
    /**
     * 응답을 만든 시각. **[dataAvailable] 이 false 면 `null`** — 갱신된 적 없는 데이터에
     * 갱신 시각을 붙이면 비어 있는 화면이 최신처럼 보인다.
     */
    val lastUpdated: String?,
    /** 연동된 활성 채널이 있는가. [dataAvailable] 과 별개다 — 연동해도 수집 전일 수 있다. */
    val isConnected: Boolean,
    /**
     * 최근 7일 안에 **측정된 분석 행이 하나라도 있는가.**
     *
     * `false` 면 [metrics] 의 `currentValue`/`previousValue` 0 은 측정값이 아니라
     * 계산을 진행시키려고 만든 **합성 자리 채우기**다. 클라이언트는 지표 카드 대신
     * "아직 수집된 데이터가 없습니다" 같은 빈 상태를 보여야 한다.
     *
     * 예전에는 이 구분이 없어서, 데이터가 한 줄도 없는 신규 사용자와 어제 실제로 0 건을
     * 기록한 사용자가 **완전히 같은 응답**을 받았다. `changePercent = null` /
     * `trend = UNKNOWN` 만으로는 그 0 의 출처를 알 수 없다.
     *
     * 행이 하나라도 있으면 그 안의 0 은 유효한 측정값이므로 `true` 다.
     *
     * 지표별이 아니라 응답 단위인 이유: 6 개 지표가 모두 같은
     * [com.ongo.domain.analytics.AnalyticsRepository.getDailyAggregates] 결과에서
     * 나오므로 가용성이 지표마다 갈릴 수 없다. 지표별 플래그는 없는 세분성을
     * 있는 것처럼 보이게 한다.
     */
    val dataAvailable: Boolean,
)

/**
 * 개별 지표 카드 데이터.
 * 프론트엔드 LiveMetric 타입과 매핑.
 */
data class LiveMetricResponse(
    val type: String,
    /**
     * 현재 기간의 측정값. **이 지표를 수집하는 플랫폼이 하나도 없으면 `null`.**
     *
     * 여섯 지표 중 `SUBSCRIBERS`·`WATCH_TIME`·`REVENUE` 는 `YouTubeClient` 만 조회한다
     * ([com.ongo.application.analytics.PlatformMetricAvailability]). TikTok·Instagram 만
     * 쓰는 크리에이터에게 그 세 카드는 **"오늘 0" 이 아니라 물어볼 곳이 없다는 뜻**인데,
     * 예전에는 실제로 0 건이었던 사용자와 완전히 같은 모양으로 나갔다.
     */
    val currentValue: Long?,
    /** 이전 기간의 **측정값**. 0 은 "어제 0 건"이라는 사실이므로 그대로 둔다. */
    val previousValue: Long?,
    /**
     * 이전 기간 대비 증감률(%). **[previousValue] 가 0 이면 `null`** — 비율을 계산할
     * 기준이 없다는 뜻이다.
     *
     * 예전에는 그 자리에 `previous=0 && current>0` 이면 `100.0`, `0 → 0` 이면 `0.0` 을
     * 넣었다. 그래서 첫 조회수 50,000 을 낸 채널과 100 → 200 으로 는 채널이 똑같이
     * "▲100%" 로 보였고, 데이터가 없는 지표는 "변화 없음"으로 보였다.
     * 도메인 [com.ongo.domain.analytics.MetricChange] 의 정책을 그대로 쓴다.
     *
     * 클라이언트는 `null` 을 0 이나 100 으로 채우지 말고 비교 불가로 표시해야 한다.
     * 이때 [trend] 도 함께 `UNKNOWN` 이다.
     */
    val changePercent: Double?,
    /**
     * `UP` / `DOWN` / `STABLE` / `UNKNOWN`.
     *
     * `UNKNOWN` 은 비교 기준이 없어 방향을 말할 수 없는 경우다
     * ([com.ongo.application.analytics.LiveDashboardUseCase.TREND_UNKNOWN] 참고).
     */
    val trend: String,
    /** [currentValue] 가 `null` 인 이유. 값이 있으면 `null`. */
    val unavailableReason: String? = null,
    val history: List<LiveMetricPointResponse>,
)

data class LiveMetricPointResponse(
    val timestamp: String,
    val value: Long,
)

data class LiveAlertResponse(
    val id: Long,
    val type: String,
    val title: String,
    val description: String,
    val metric: String,
    /**
     * 알림을 발생시킨 실제 값과 임계값. **둘 다 항상 `null`** 이다.
     *
     * `LiveAlert` 도메인(`LiveAlert.kt`)에는 `type`·`message`·`severity` 뿐이고 값이나
     * 임계값을 담는 컬럼이 없다. 예전에는 그 자리에 `0L` 을 하드코딩해 내려보냈고,
     * 화면이 그리면 "조회수 0 이 임계값 0 을 넘었다" 는 말이 된다.
     *
     * 실제 값을 보여주려면 `live_alerts` 에 컬럼이 필요하다 — 스키마 변경 전까지는
     * 없다는 사실을 `null` 로 정직하게 알린다.
     */
    val value: Long?,
    val threshold: Long?,
    val createdAt: String?,
    val read: Boolean,
)

data class LiveAlertConfigResponse(
    val id: Long,
    val type: String,
    val enabled: Boolean,
    val threshold: Int,
)

data class UpdateLiveAlertConfigRequest(
    val id: Long,
    val type: String,
    val enabled: Boolean,
    val threshold: Int,
)

data class HeatmapRecommendationResponse(
    val dayOfWeek: Int,
    val hour: Int,
    val score: Double,
    val reason: String,
)
