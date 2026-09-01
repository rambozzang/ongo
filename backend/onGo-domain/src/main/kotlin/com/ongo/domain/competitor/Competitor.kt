package com.ongo.domain.competitor

import java.time.LocalDateTime

data class Competitor(
    val id: Long? = null,
    val userId: Long,
    val platform: String,
    val platformChannelId: String,
    val channelName: String,
    val channelUrl: String? = null,
    /**
     * 구독자 수. **재지 못했으면 `null`** — `0` 은 실제로 0 명이라는 관측이다.
     *
     * `competitors.subscriber_count` 는 `NOT NULL` 이 아니고 기본값만 `0` 이다
     * (`V4__analytics_tables.sql:9`). 자동 조회가 값을 주지 못하거나(구독자 수를 숨긴
     * 채널) 수동 입력에서 비워 두면 그 사실을 그대로 담는다.
     *
     * 이것은 연동 채널(`com.ongo.domain.channel.Channel`)과 **별개 모델**이다 —
     * 저쪽은 우리 토큰으로 조회하고, 이쪽은 공개 정보만 본다.
     */
    val subscriberCount: Long? = null,
    /**
     * 총 조회수. **재지 못했으면 `null`** — `0` 은 실제로 조회가 없었다는 관측이다.
     *
     * `competitors.total_views` 는 `NOT NULL` 이 아니다(`V4__analytics_tables.sql:10`).
     * [avgViews] 의 분자이기도 하다.
     */
    val totalViews: Long? = null,
    /**
     * 영상 수. **재지 못했으면 `null`** — `0` 은 실제로 영상이 없다는 관측이다.
     *
     * `competitors.video_count` 는 `NOT NULL` 이 아니다(`V4__analytics_tables.sql:11`).
     * 이 값은 [avgViews] 가 측정값인지 판정하는 **분모**로도 쓰인다.
     */
    val videoCount: Int? = null,
    val avgViews: Long = 0,
    val profileImageUrl: String? = null,
    val lastSyncedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)

/**
 * 측정된 영상당 평균 조회수. **분자나 분모를 모르면 `null`.**
 *
 * 저장 모델(`avg_views`)은 non-null 이라 계산하지 못한 자리에 `0` 이 남는다. 그 `0` 은
 * "평균 0회" 라는 관측이 아니라 **계산할 수 없었던 자리**다. 그래서 값을 내보내는 쪽에서
 * 근거([videoCount]·[totalViews])를 보고 갈라낸다 — 스키마는 그대로 둔다.
 *
 * - 분모를 모르거나(`videoCount == null`) 0 이면 나눌 대상이 없다.
 * - 분자를 모르면(`totalViews == null`) 평균의 근거가 없다.
 * - 영상이 있고 조회수가 실제로 0 이면 `0` 은 관측이므로 그대로 낸다.
 *
 * **응답 DTO 와 AI 프롬프트가 같은 규칙을 써야 한다.** 한쪽만 고치면 화면은 "측정 불가"
 * 인데 유료 프롬프트에는 "평균 0회" 가 들어가는 모순이 난다.
 */
fun measuredAvgViews(videoCount: Int?, totalViews: Long?, storedAvgViews: Long): Long? =
    if (videoCount != null && videoCount > 0 && totalViews != null) storedAvgViews else null
