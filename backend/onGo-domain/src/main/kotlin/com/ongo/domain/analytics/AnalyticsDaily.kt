package com.ongo.domain.analytics

import java.time.LocalDate
import java.time.LocalDateTime

data class AnalyticsDaily(
    val id: Long? = null,
    val videoUploadId: Long,
    val date: LocalDate,
    val views: Int = 0,
    val likes: Int = 0,
    val commentsCount: Int = 0,
    val shares: Int = 0,
    val watchTimeSeconds: Long = 0,
    val subscriberGained: Int = 0,
    /** [revenueStatus] 가 [RevenueStatus.MEASURED] 일 때만 의미가 있다. */
    val revenueMicro: Long = 0,
    val revenueCurrency: String? = null,
    val revenueStatus: RevenueStatus = RevenueStatus.UNSUPPORTED,
    val impressions: Int = 0,
    val avgViewDurationSeconds: Int = 0,
    /**
     * [views]·[likes]·[commentsCount]·[shares] 가 무엇을 뜻하는지 (V115).
     *
     * **기본값이 [EngagementBasis.LEGACY_CUMULATIVE] 인 것은 의도적이다.** 라벨을 지정하지
     * 않고 만든 행은 합계에서 빠진다. 반대 기본값을 두면 누락된 라벨이 조용히 합계를
     * 부풀린다 — 빠지는 쪽은 눈에 띄고 섞이는 쪽은 눈에 띄지 않는다.
     */
    val engagementBasis: EngagementBasis = EngagementBasis.LEGACY_CUMULATIVE,
    /**
     * 관측 시점의 평생 누적 스냅샷. 다음 주기의 증분을 구하는 기준선이다.
     *
     * 기간값을 주는 플랫폼(YouTube)은 누적을 받지 않으므로 `null` 이다.
     */
    val viewsTotal: Long? = null,
    val likesTotal: Long? = null,
    val commentsTotal: Long? = null,
    val sharesTotal: Long? = null,
    val createdAt: LocalDateTime? = null,
)
