package com.ongo.domain.ugc.analytics

import java.time.LocalDateTime

/**
 * 게시물 지표 스냅샷. 플랫폼 지표 동기화 결과를 원본 그대로 저장하고,
 * 캠페인 대시보드는 게시물별 최신 스냅샷을 합산한다.
 */
data class MetricSnapshot(
    val id: Long? = null,
    val campaignPostId: Long,
    val capturedAt: LocalDateTime,
    val views: Long = 0,
    val likes: Long = 0,
    val comments: Long = 0,
    val shares: Long = 0,
)
