package com.ongo.application.ugc.dto

import java.time.LocalDateTime

/**
 * 게시물 한 건의 지표.
 *
 * 값이 `null` 이면 **측정하지 않았다** — 0 이 아니다. 셋 중 하나다.
 *
 * - 플랫폼이 그 지표를 API 로 주지 않는다(Facebook 공유, Pinterest 댓글 …)
 * - 아직 스냅샷이 하나도 없다
 * - V110 이전 행이라 출처를 알 수 없고 값이 0 이다
 *
 * 소비자는 이 자리를 0 으로 채우지 말고 "측정 불가"로 표시해야 한다. 브랜드 성과와
 * 보상 판단이 이 숫자 위에서 이뤄진다.
 */
data class PostMetricResponse(
    val campaignPostId: Long,
    val platform: String,
    val postStatus: String,
    val views: Long?,
    val likes: Long?,
    val comments: Long?,
    val shares: Long?,
    val capturedAt: LocalDateTime?,
    /** 이 게시물에서 측정하지 못한 지표 이름들. 화면이 이유를 설명하는 데 쓴다. */
    val unavailableMetrics: List<String> = emptyList(),
)

/**
 * 캠페인 합계.
 *
 * 합계가 `null` 이면 **그 지표를 측정한 게시물이 하나도 없다.** 0 을 넣으면
 * "공유 0회" 라는 성과 보고가 되어 보상 판단이 그 위에서 이뤄진다.
 *
 * 측정된 게시물이 하나라도 있으면 **그 게시물들만** 더한다. 미측정 게시물을 0 으로
 * 섞으면 합계는 실제보다 작아지면서도 측정값처럼 보인다.
 */
data class CampaignAnalyticsResponse(
    val campaignId: Long,
    val totalViews: Long?,
    val totalLikes: Long?,
    val totalComments: Long?,
    val totalShares: Long?,
    val lastSyncedAt: LocalDateTime?,
    val posts: List<PostMetricResponse>,
    /**
     * 지표별로 **합계에 실제로 들어간 게시물 수**.
     *
     * 합계만 보면 "10개 중 2개만 측정됐다"를 알 수 없다. 브랜드가 성과를 판단할 때
     * 표본 크기를 함께 봐야 한다.
     */
    val measuredPostCounts: Map<String, Int> = emptyMap(),
)

data class RecordMetricRequest(
    val views: Long = 0,
    val likes: Long = 0,
    val comments: Long = 0,
    val shares: Long = 0,
)
