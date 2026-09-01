package com.ongo.domain.analytics

data class DashboardKpi(
    /**
     * 기간 내 조회수 합계. **조회수를 수집하는 플랫폼의 행이 없으면 `null`** 이다.
     *
     * `TumblrClient.kt:141` 은 `views = total_notes`(좋아요+리블로그+답글 총합)를 같은
     * 컬럼에 넣는다. 하드코딩 0 과 달리 **다른 뜻의 큰 숫자**라 합계를 오염시키므로,
     * 조회수를 실제로 보고하는 플랫폼의 행만 더한다.
     *
     * - **보고 플랫폼의 행이 있고 합계가 0** → 실제로 0 회였다는 관측. `0` 이다.
     * - **보고 플랫폼이 없거나 그 기간에 행이 없다** → 물어볼 곳이 없다. `null`.
     */
    val totalViews: Long?,
    /**
     * 이전 기간 대비 증감률(%).
     *
     * **`null` 인 경우 두 가지** — 이전 기간이 0 이라 비율의 기준이 없거나
     * ([MetricChange] 참고), 두 기간 중 하나라도 측정되지 않았거나.
     */
    val totalViewsChange: Double?,
    /**
     * 신규 구독(팔로워) 증가 수. **측정된 행이 없으면 `null`** 이다.
     *
     * `subscriber_gained` 를 실제로 조회하는 어댑터는 `YouTubeClient` 하나뿐이다
     * (`YouTubeClient.kt:149` metrics 목록). 나머지 12개는 `0` 을 하드코딩한다.
     *
     * 그래서 두 상황을 구분해야 한다.
     *
     * - **YouTube 업로드의 집계 행이 있고 합계가 0** → 실제로 0명이 늘었다는 관측.
     * - **YouTube 업로드가 없거나 그 기간에 행이 없다** → 물어볼 곳이 없다. `null`.
     *
     * 예전에는 둘 다 `0` 이었고, 업로드가 아예 없을 때도 `0` 을 돌려줬다.
     */
    val totalSubscribers: Long?,
    /**
     * 절대 증감 수(비율이 아니다). **현재·이전 기간 중 하나라도 측정되지 않았으면 `null`.**
     *
     * 예전에는 `currentSubs - previousSubs` 를 무조건 계산해, 양쪽 다 미측정일 때
     * `0 - 0 = 0` 이 "변화 없음" 이라는 관측으로 보였다.
     */
    val totalSubscribersChange: Long?,
    /**
     * 기간 내 좋아요 합계. **좋아요를 수집하는 플랫폼의 행이 없으면 `null`.**
     *
     * `PinterestClient.kt:158` 은 `likes = SAVE`(저장 수)를 넣는다. 저장은 좋아요가
     * 아니므로 [totalViews] 와 같은 이유로 보고 플랫폼의 행만 더한다.
     */
    val totalLikes: Long?,
    /** 이전 기간 대비 증감률(%). 기준이 없거나 한쪽 기간이 미측정이면 **`null`**. */
    val totalLikesChange: Double?,
    val creditBalance: Int,
    val creditTotal: Int,
    /**
     * 기간 내 댓글 합계. **댓글을 수집하는 플랫폼의 행이 없으면 `null`.**
     *
     * `PinterestClient.kt:159` 처럼 조회하지도 않고 `0` 을 채우는 어댑터가 있다.
     */
    val totalComments: Long? = null,
)
