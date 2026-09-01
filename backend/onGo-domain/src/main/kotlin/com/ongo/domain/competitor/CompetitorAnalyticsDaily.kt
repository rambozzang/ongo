package com.ongo.domain.competitor

import java.time.LocalDate
import java.time.LocalDateTime

data class CompetitorAnalyticsDaily(
    val id: Long? = null,
    val competitorId: Long,
    val date: LocalDate,
    /**
     * 그날 관측한 구독자 수. **조회가 값을 주지 못했으면 `null`.**
     *
     * 이 스냅샷은 성장률의 두 끝점이 된다. 재지 못한 날에 `0` 을 기록하면
     * 어제 10,000 → 오늘 0 이 되어 **-100% 라는 폭락을 지어낸다.** 컬럼은
     * `NOT NULL` 이 아니다(`V61__create_competitor_analytics_daily.sql:22`).
     */
    val subscriberCount: Long? = null,
    /**
     * 그날 관측한 영상 수. **조회가 값을 주지 못했으면 `null`.**
     *
     * 컬럼은 `NOT NULL` 이 아니다(`V61__create_competitor_analytics_daily.sql:23`).
     * 그날의 평균 조회수를 판정하는 분모다 — 0 으로 기록하면 "그날 영상 0개" 가 된다.
     */
    val videoCount: Int? = null,
    val avgViews: Long = 0,
    val avgLikes: Long = 0,
    val avgComments: Long = 0,
    /**
     * 그날 관측한 총 조회수. **조회가 값을 주지 못했으면 `null`.**
     *
     * 컬럼은 `NOT NULL` 이 아니다(`V61__create_competitor_analytics_daily.sql:27`).
     */
    val totalViews: Long? = null,
    val createdAt: LocalDateTime? = null,
)
