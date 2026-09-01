package com.ongo.application.analytics.dto

/**
 * 코호트 하나. **조회수가 하나도 측정되지 않았으면 정규화할 기준이 없다.**
 *
 * 예전에는 `maxViews = totalViews.coerceAtLeast(1)` 로 분모를 1 로 만들어, 조회가 전혀
 * 없는 코호트의 모든 구간이 `normalizedPercent = 0.0` 이 됐다. 화면은 그것을 **평평한
 * 0% 유지 곡선**으로 그렸다 — 재지 않았을 뿐인데 "끝까지 아무도 안 봤다" 는 관측이 된다.
 */
data class CohortGroupResponse(
    val name: String,
    /** 이 코호트에 속한 영상 수. 실제 개수이므로 항상 값이 있다. */
    val videoCount: Int,
    /** 영상당 평균 조회수. **영상이 없으면 `null`** — 나눌 것이 없다. */
    val avgViews: Long?,
    val cumulativeViewCurve: List<DataPoint>,
    /** 곡선을 정규화할 수 없었던 이유. 정규화됐으면 `null`. */
    val unavailableReason: String? = null,
)

data class DataPoint(
    val day: Int,
    /** 그 구간까지의 누적 조회수. 측정된 0 은 그대로 0 이다. */
    val value: Long,
    /**
     * 최대 누적 조회수 대비 비율(%). **기준이 될 최대값이 0 이면 `null`.**
     *
     * 기준이 있는 상태에서의 `0.0` 은 "그 구간까지 조회가 없었다" 는 관측이므로 유지한다.
     */
    val normalizedPercent: Double?,
)

data class CohortAnalysisResponse(
    val groupBy: String,
    val cohorts: List<CohortGroupResponse>,
    val dateRange: DateRangeInfo,
)

data class DateRangeInfo(
    val from: String,
    val to: String,
)

data class RetentionDataPoint(
    val timestamp: Int,
    val retentionRate: Double,
    val viewCount: Long,
)

data class DropOffPoint(
    val timestamp: Int,
    val dropRate: Double,
    val possibleReason: String,
)

data class RetentionCurveResponse(
    val videoId: Long,
    val retentionPoints: List<RetentionDataPoint>,
    val avgRetention: List<RetentionDataPoint>,
    val dropOffPoints: List<DropOffPoint>,
    /**
     * 구간별 유지율을 실제로 측정했는지. **기본값은 false** — 판단 근거가 없으면
     * 측정된 것으로 보지 않는다.
     */
    val available: Boolean = false,
    val unavailableReason: String? = null,
)
