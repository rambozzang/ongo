package com.ongo.domain.analytics

import java.time.LocalDateTime

/**
 * 영상 성과 점수. **계산할 수 없는 축은 `null`** 이다.
 *
 * 모든 하위 점수는 비율이라 분모(조회수)나 비교 기준(채널 평균)이 없으면 성립하지 않는다.
 * `0.0` 으로 채우면 "그 축에서 최하위"라는, 측정한 적 없는 판정이 된다.
 */
data class VideoPerformanceScore(
    val videoId: Long,
    /** 측정된 하위 점수의 가중 평균. 계산된 축이 하나도 없으면 `null`. */
    val overallScore: Double?,
    val viewVelocityScore: Double?,
    val engagementScore: Double?,
    val watchTimeScore: Double?,
    val conversionScore: Double?,
    val shareScore: Double?,
    val isAnomaly: Boolean = false,
    val anomalyType: AnomalyType? = null,
    /** 향후 7일 예상 조회수. 회귀선을 그을 점이 부족하면 `null` — 관측 합계로 대체하지 않는다. */
    val predictedViews7d: Long? = null,
    /** 값이 `null` 인 축과 그 이유. 값과 함께 만들어 어긋나지 않게 한다. */
    val unavailableMetrics: Map<String, String> = emptyMap(),
    val calculatedAt: LocalDateTime = LocalDateTime.now(),
)

enum class AnomalyType {
    VIRAL_SPIKE,
    ENGAGEMENT_SURGE,
    UNUSUAL_DROP,
    SHARE_SPIKE,
}
