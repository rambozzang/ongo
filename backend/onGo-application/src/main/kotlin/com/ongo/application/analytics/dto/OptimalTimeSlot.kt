package com.ongo.application.analytics.dto

/**
 * 한 시간대 슬롯의 성과 요약.
 *
 * ## [engagementRate] 가 `null` 일 수 있는 이유
 *
 * 이 슬롯을 만든 게시물이 전부 **참여 지표를 하나도 보고하지 않는 플랫폼**의 것이면
 * 참여율 표본이 하나도 없다. 예전에는 그 자리에 빈 목록의 중앙값 `0.0` 이 들어가
 * 화면과 AI 프롬프트에 **"참여율 0%"** 로 나갔다 — 재지 않았을 뿐인데 "참여가 전혀
 * 없던 시간대" 라는 관측이 된다.
 *
 * **보고하는 플랫폼의 측정된 0 은 그대로 `0.0`** 이다. 그것은 관측이다.
 */
data class OptimalTimeSlot(
    val dayOfWeek: Int,
    val dayLabel: String,
    val hour: Int,
    val timeLabel: String,
    val expectedViews: Long,
    /** 참여율(%). **표본이 하나도 없으면 `null`** — 0 으로 채우지 말 것. */
    val engagementRate: Double?,
    val confidenceScore: Double,
    /**
     * 정렬용 종합 점수.
     *
     * **참여율이 측정되지 않은 슬롯이 하나라도 있으면 참여 항은 모든 슬롯에서 빠진다.**
     * 그래야 슬롯끼리 같은 구성요소로 비교된다. 자세한 근거는
     * `AnalyticsUseCase.getOptimalPublishTimes` 주석 참고.
     */
    val score: Double,
)

/**
 * 최적 게시 시간 추천.
 *
 * 슬롯이 비어 있으면 **추천할 근거가 없다**는 뜻이다. 게시 시각(`video_uploads.published_at`)
 * 이 확인된 성과 데이터가 없으면 시각을 추천할 수 없다.
 *
 * 예전에는 `analytics_daily.created_at`(동기화 시각)을, 그것도 없으면 **정오**를 써서
 * 슬롯을 만들었다. 화면은 그 시각을 "예상 조회수 · 참여율 · 신뢰도" 와 함께 추천으로
 * 보여줬다.
 */
data class OptimalTimesResponse(
    val slots: List<OptimalTimeSlot>,
    /** [slots] 가 비어 있는 이유. 화면이 그대로 보여줄 수 있는 문장. */
    val unavailableReason: String? = null,
)
