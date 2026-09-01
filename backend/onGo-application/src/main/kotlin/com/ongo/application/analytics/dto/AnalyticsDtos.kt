package com.ongo.application.analytics.dto

import com.ongo.common.enums.Platform
import java.time.LocalDate

data class DashboardKpiResponse(
    /**
     * 기간 내 조회수 합계. **조회수를 수집하는 플랫폼의 행이 없으면 `null`** 이다.
     *
     * Tumblr 는 `total_notes`(좋아요+리블로그+답글 총합)를 `views` 컬럼에 넣는다
     * (`TumblrClient.kt:141`). 그런 행을 빼고 나면 더할 것이 없는 사용자가 생기는데,
     * 그때의 `0` 은 "0 회" 가 아니라 물어볼 곳이 없다는 뜻이다.
     *
     * 화면은 `?? 0` 하지 말고 "측정 불가"로 표시해야 한다. **보고 플랫폼의 행이 있는
     * 상태의 0 은 관측**이므로 그대로 `0` 이다.
     */
    val totalViews: Long?,
    /**
     * 이전 기간 대비 증감률(%). **`null` 이면 비교 불가.**
     *
     * 이전 기간이 0 이라 비율의 기준이 없거나, 두 기간 중 하나라도 측정되지 않은
     * 경우다. 화면은 이것을 0 이나 100 으로 채우지 말고 증감 배지 자체를 그리지 말아야
     * 한다. 예전에는 이 자리에 임의의 `100.0` 이 들어가, 첫 주에 5만 조회를 낸 채널과
     * 100 → 200 으로 는 채널이 똑같이 "▲100%" 로 보였다.
     */
    val viewsChangePercent: Double?,
    /**
     * 신규 구독(팔로워) 증가 수. **측정된 행이 없으면 `null`** 이다.
     *
     * `subscriber_gained` 를 조회하는 어댑터는 `YouTubeClient` 하나뿐이라, YouTube 업로드가
     * 없거나 그 기간에 집계 행이 없으면 물어볼 곳 자체가 없다. **YouTube 행이 있는 상태의
     * 0 은 "0명 늘었다" 는 관측**이므로 그대로 `0` 이다.
     *
     * 화면은 `null` 을 0 으로 채우지 말고 "측정 불가"로 표시해야 한다.
     */
    val totalSubscribers: Long?,
    /** 절대 증감 수(비율이 아니다). 현재·이전 기간 중 하나라도 미측정이면 `null`. */
    val subscribersChange: Long?,
    /**
     * 기간 내 좋아요 합계. **좋아요를 수집하는 플랫폼의 행이 없으면 `null`.**
     *
     * Pinterest 는 `SAVE`(저장 수)를 `likes` 에 넣는다(`PinterestClient.kt:158`).
     * 저장은 좋아요가 아니므로 그 행은 빠진다.
     */
    val totalLikes: Long?,
    /** 이전 기간 대비 증감률(%). 기준이 없거나 한쪽 기간이 미측정이면 `null`. */
    val likesChangePercent: Double?,
    val creditBalance: Int,
    val creditTotal: Int,
    /** 기간 내 댓글 합계. **댓글을 수집하는 플랫폼의 행이 없으면 `null`.** */
    val totalComments: Long? = null,
)

/**
 * 하루치 추세. **수집하는 플랫폼의 행만 담는다.**
 *
 * `subscriber_gained` 컬럼을 채우는 어댑터는 `YouTubeClient` 하나뿐이라, 예전에는 나머지
 * 12개 플랫폼의 하드코딩 0 이 합계에 들어가고 [platformSubscribers] 에도 플랫폼마다
 * "+0" 이 실렸다. 화면은 그것을 "신규 구독 0명" 이라는 성과로 그렸다.
 */
data class TrendPoint(
    val date: LocalDate,
    val totalViews: Long,
    /** 조회수를 수집하는 플랫폼만. 키가 없다는 것이 곧 미수집이다. */
    val platformViews: Map<String, Long>,
    /** 구독 증가를 수집하는 플랫폼이 하나도 없으면 `null`. `0` 은 "늘지 않았다" 는 관측이 된다. */
    val totalSubscribers: Long? = null,
    /** 구독 증가를 수집하는 플랫폼만. */
    val platformSubscribers: Map<String, Long> = emptyMap(),
    val unavailableMetrics: Set<String> = emptySet(),
)

data class TrendDataResponse(
    val data: List<TrendPoint>
)

/**
 * 플랫폼 하나의 합계. **그 플랫폼이 수집하지 않는 지표는 `null`** 이다.
 *
 * 0 만 문제가 아니다. Pinterest 의 `shares` 자리에는 PIN_CLICK(클릭 수), Dailymotion 에는
 * bookmarks_total(북마크), Tumblr 의 `views` 자리에는 total_notes(노트 총합)가 들어 있었다 —
 * **이름이 다른 지표라 0 이 아니라 큰 숫자로 조용히 틀린다.**
 */
data class PlatformAnalyticsDetail(
    val platform: Platform,
    val views: Long?,
    val likes: Long?,
    val comments: Long?,
    val shares: Long?,
    /**
     * 이 플랫폼이 **수집하지 않는** 지표 이름. 플랫폼 계약의 문제라 기간과 무관하다.
     *
     * 값이 `null` 인 이유를 이 집합으로 가른다.
     *
     * - 지표가 **여기 있음** → 플랫폼이 주지 않는다(영원히 못 잼).
     * - 지표가 **여기 없음** → 수집하지만 그 기간에 집계 행이 없다(수집 대기).
     *
     * 행이 있고 합이 `0` 이면 그 `0` 은 실측이므로 숫자로 남는다.
     */
    val unavailableMetrics: Set<String> = emptySet(),
    /**
     * 일별 추이. 조회수를 수집하지 않는 플랫폼은 비어 있다 — 그릴 추이가 없다.
     *
     * 기간에 집계 행이 없어도 비어 있다. 즉 **비어 있음은 "그 기간에 측정된 행이 없다"** 를
     * 뜻하며, 위 합계가 `null` 인 것과 짝을 이룬다.
     */
    val dailyData: List<DailyMetric>
)

data class DailyMetric(
    val date: LocalDate,
    val views: Int,
    val likes: Int,
    val comments: Int,
    val shares: Int = 0,
)

data class VideoAnalyticsResponse(
    val videoId: Long,
    val title: String?,
    val platforms: List<PlatformAnalyticsDetail>
)

data class HeatmapResponse(
    val data: Map<String, Map<Int, Long>>
)

/**
 * 인기 영상 한 편. **잰 적이 없는 합계는 `null`** 이다.
 *
 * `null` 인 이유는 두 가지이고 [unavailableMetrics] 로 갈린다.
 *
 * - 지표가 [unavailableMetrics] 에 **있음** → 그 지표를 주는 업로드가 없다(측정 불가).
 * - 지표가 [unavailableMetrics] 에 **없음** → 수집하지만 그 기간에 행이 없다(수집 대기).
 *
 * 숫자는 실측이다 — 행이 있고 합이 `0` 이면 그 `0` 은 관측이므로 그대로 `0` 이다.
 */
data class TopVideoItem(
    val id: Long,
    val title: String,
    val thumbnailUrl: String?,
    val totalViews: Long?,
    val totalLikes: Long? = null,
    /**
     * 그 지표를 **주는 업로드가 하나도 없는** 지표 이름. 플랫폼 계약의 문제이므로
     * 기간과 무관하다. 기간에 행이 없어 `null` 인 경우는 여기에 들어가지 않는다.
     */
    val unavailableMetrics: Set<String> = emptySet(),
    val publishedAt: java.time.LocalDateTime? = null,
    val platforms: List<String>
)

data class TopVideoResponse(
    val videos: List<TopVideoItem>
)

/**
 * 플랫폼 하나의 성과 합계.
 *
 * ## `null` 은 "그 플랫폼이 이 지표를 주지 않는다" 이며 0 이 아니다
 *
 * Facebook·WordPress·Vimeo 는 공유 수를, Pinterest 는 댓글 수를 API 로 주지 않는다
 * ([com.ongo.application.analytics.PlatformMetricAvailability]). 저장된 0 은 측정값이
 * 아니라 자리 채우기인데, 예전에는 그대로 숫자로 내려보내 플랫폼 비교 화면이
 * "Facebook 공유 0회" 를 성과처럼 보여줬다.
 *
 * 같은 클래스의 `getVideoComparison` 은 이미 [unavailableMetrics] 계약을 쓴다.
 * 그 계약을 여기에도 맞춘다.
 *
 * 지원하는 지표의 실제 0 은 관측 결과이므로 그대로 `0` 이다.
 */
data class PlatformSummary(
    val platform: Platform,
    /**
     * 조회수. **그 플랫폼이 보고하지 않으면 `null`.**
     *
     * 예전 주석은 "모든 플랫폼이 보고한다" 였지만 사실이 아니다. Tumblr 의 `views` 자리에는
     * `total_notes`(노트 총합)가, Naver Clip 에는 아무것도 들어 있지 않다.
     */
    val views: Long?,
    /** 좋아요. **그 플랫폼이 보고하지 않으면 `null`.** */
    val likes: Long?,
    /** 댓글. Pinterest 는 주지 않는다. */
    val comments: Long?,
    /** 공유. Facebook·WordPress·Vimeo 는 주지 않는다. */
    val shares: Long?,
    /**
     * 이 플랫폼이 수집하지 않는 지표 이름들. 화면이 이유를 설명하는 데 쓴다.
     *
     * `getVideoComparison` 의 `VideoCompareItem.unavailableMetrics` 와 같은 계약이다.
     */
    val unavailableMetrics: List<String> = emptyList(),
)

data class PlatformComparisonResponse(
    val platforms: List<PlatformSummary>
)
