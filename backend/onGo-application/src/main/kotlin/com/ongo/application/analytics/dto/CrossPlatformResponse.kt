package com.ongo.application.analytics.dto

data class CrossPlatformComparisonResponse(
    val videoId: Long,
    val videoTitle: String?,
    val platforms: List<PlatformMetrics>,
    val bestPlatform: String?,
    val insights: List<String>,
)

/**
 * 플랫폼 하나의 지표. **그 플랫폼이 수집하지 않는 값은 `null`** 이다.
 *
 * 예전에는 `unavailableMetrics` 로 "이건 미수집" 이라고 알리면서도 **숫자는 raw 그대로**
 * 내보냈다. 그 숫자를 읽는 소비자는 Pinterest 의 `likes` 에서 저장(Save) 수를,
 * `shares` 에서 클릭 수를, Tumblr 의 `views` 에서 노트 총합을 받는다.
 */
data class PlatformMetrics(
    val platform: String,
    val views: Long?,
    val likes: Long?,
    val comments: Long?,
    val shares: Long?,
    val watchTimeSeconds: Long?,
    /** 수집하는 참여 지표만 분자로, 같은 행의 조회수를 분모로. 분모가 없으면 `null`. */
    val engagementRate: Double?,
    val avgViewDuration: Long?,
    val revenueMicro: Long?,
    /** Numeric zero can mean "not provided"; expose that distinction to clients. */
    val unavailableMetrics: Set<String> = emptySet(),
)

data class CrossPlatformSummaryResponse(
    val videos: List<CrossPlatformComparisonResponse>,
    val platformRankings: Map<String, PlatformRanking>,
)

/**
 * 플랫폼 순위. **조회수를 수집하지 않는 플랫폼은 순위를 매기지 않는다.**
 *
 * 예전에는 raw 합계로 정렬해, Tumblr 의 노트 총합이 조회수로 들어가 그 플랫폼을
 * 최상위로 올릴 수 있었다.
 */
data class PlatformRanking(
    val platform: String,
    val avgEngagementRate: Double?,
    val totalViews: Long?,
    val totalRevenue: Long?,
    /** 조회수가 측정되지 않으면 순위를 매길 수 없다 → `null`. */
    val rank: Int?,
    val unavailableMetrics: Set<String> = emptySet(),
)
