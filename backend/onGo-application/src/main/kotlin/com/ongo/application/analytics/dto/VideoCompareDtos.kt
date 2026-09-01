package com.ongo.application.analytics.dto

data class VideoCompareResponse(
    val videos: List<VideoCompareItem>,
)

/**
 * 영상 비교 한 줄. **그 지표를 수집하는 업로드가 하나도 없으면 합계는 `null`** 이다.
 *
 * 예전에는 참여율만 플랫폼 가용성을 봤고 나머지 합계는 raw 였다. 같은 응답 안에서 참여율은
 * Facebook 공유를 빼고 계산하는데 `totalShares` 에는 그 0 이 들어가는 모순이 있었다.
 *
 * 0 만 문제가 아니다 — Pinterest 의 PIN_CLICK(클릭 수)과 Dailymotion 의
 * bookmarks_total(북마크)이 `totalShares` 에, Tumblr 의 total_notes(노트 총합)가
 * `totalViews` 에 그대로 더해졌다.
 */
data class VideoCompareItem(
    val videoId: Long,
    val title: String?,
    val totalViews: Long?,
    val totalLikes: Long?,
    val totalComments: Long?,
    val totalShares: Long?,
    val totalWatchTimeSeconds: Long?,
    val avgDailyViews: Long?,
    /** 좋아요·댓글·공유를 모두 수집하는 업로드의 행에서만 계산한다. 분모가 없으면 `null`. */
    val engagementRate: Double?,
    /** The aggregate is incomplete when one of its platform uploads cannot provide a metric. */
    val unavailableMetrics: Set<String> = emptySet(),
)
