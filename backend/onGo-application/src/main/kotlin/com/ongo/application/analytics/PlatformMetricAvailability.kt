package com.ongo.application.analytics

/**
 * Metrics that the current platform adapters do not fetch.
 *
 * The analytics table stores numeric columns, so an unavailable value is
 * currently persisted as zero. Keeping this contract next to the response
 * mapping prevents that implementation detail from being presented as a
 * measured zero to a creator.
 */
object PlatformMetricAvailability {
    const val VIEWS = "views"
    const val LIKES = "likes"
    const val COMMENTS = "comments"
    const val SHARES = "shares"
    const val WATCH_TIME_SECONDS = "watchTimeSeconds"
    const val AVG_VIEW_DURATION = "avgViewDuration"
    const val REVENUE_MICRO = "revenueMicro"

    /**
     * 노출 수.
     *
     * `YouTubeClient` 만 Analytics API 의 `impressions` 지표를 요청한다
     * (`YouTubeClient.kt:149` 의 metrics 목록). 나머지 12 개 어댑터는 이 값을 아예 세팅하지
     * 않아 `analytics_daily.impressions` 가 기본값 0 으로 남는다.
     *
     * 그 0 을 측정값으로 쓰면 CTR 의 분모가 사라진다 — [com.ongo.application.analytics.AnalyticsUseCase.getCTRTrend] 참고.
     */
    const val IMPRESSIONS = "impressions"

    /**
     * 구독(팔로워) 증가 수.
     *
     * `YouTubeClient` 만 Analytics API 의 `subscribersGained` 지표를 요청한다
     * (`YouTubeClient.kt:149` 의 metrics 목록, `:162` 파싱). 나머지 12 개 어댑터는
     * `subscriberGained = 0` 을 하드코딩해 `analytics_daily` 에 0 이 저장된다.
     *
     * 그 0 을 측정값으로 쓰면 구독 전환율의 분자가 사라지고, 화면은 "총 신규 구독 +0" 을
     * 성과처럼 보여준다 — [com.ongo.application.analytics.AnalyticsUseCase.getSubscriberConversion] 참고.
     */
    const val SUBSCRIBER_GAINED = "subscriberGained"

    /**
     * 현재 플랫폼 분석 어댑터가 조회하지 않는 공통 지표.
     *
     * 수익 컬럼은 DB 기본값이 0이라 실제 수익 0과 "수집하지 않음"을 구분하지 못한다.
     * 수집하지 않는 상태를 측정값처럼 노출하지 않도록 이 계약을 응답 계층에서도 사용한다.
     */
    private val commonUnavailableMetrics =
        setOf(WATCH_TIME_SECONDS, AVG_VIEW_DURATION, REVENUE_MICRO, IMPRESSIONS, SUBSCRIBER_GAINED)

    /**
     * 이 계약이 아는 지표 전체. **"아무것도 수집하지 않는다" 를 표현하는 값**이다.
     *
     * 알 수 없는 플랫폼과 Naver Clip 처럼 분석 API 자체가 없는 플랫폼에 쓴다.
     */
    private val allMetrics = setOf(
        VIEWS, LIKES, COMMENTS, SHARES,
        WATCH_TIME_SECONDS, AVG_VIEW_DURATION, REVENUE_MICRO, IMPRESSIONS, SUBSCRIBER_GAINED,
    )

    private val byPlatform = mapOf(
        // YouTube 만 Analytics API 로 estimatedRevenue 를 조회한다(V107 이후).
        // 실제 측정 여부는 행마다 다르므로 최종 판정은 `analytics_daily.revenue_status` 가
        // 한다 — 여기서는 "물어볼 수 있는 플랫폼인가"만 정한다.
        /*
         * YouTube 는 Analytics API 한 번에 여섯 지표를 모두 요청한다
         * (`YouTubeClient.kt:149` metrics 목록):
         * estimatedMinutesWatched · subscribersGained · impressions · averageViewDuration
         * · estimatedRevenue(V107 이후) — 그래서 공통 미수집 집합에서 전부 뺀다.
         *
         * 이 맵이 코드와 어긋나 있었다. `commonUnavailableMetrics` 가
         * WATCH_TIME_SECONDS·AVG_VIEW_DURATION 을 담고 YouTube 도 그것을 물려받아,
         * **실제로 수집하는 지표를 "미수집" 이라고 선언**하고 있었다. 그 선언을 믿는
         * 소비자는 YouTube 시청 시간을 통째로 버린다.
         *
         * 실제 측정 여부는 행마다 다르므로 최종 판정은 값 자체가 한다 —
         * 여기서는 "물어볼 수 있는 플랫폼인가" 만 정한다.
         */
        "YOUTUBE" to (
            commonUnavailableMetrics -
                REVENUE_MICRO - IMPRESSIONS - SUBSCRIBER_GAINED -
                WATCH_TIME_SECONDS - AVG_VIEW_DURATION
            ),
        /*
         * Facebook 의 `likes` 는 **반응(reaction) 전체 합계**다.
         *
         * `FacebookClient.kt:91` 이 요청하는 지표는 `total_video_reactions_by_type_total`
         * 이고 `:102` 가 그것을 `likes` 에 넣는다. 좋아요·최고예요·웃겨요 등 여섯 반응을
         * 모두 합친 수이므로 순수 좋아요보다 크다.
         *
         * **그래도 미수집으로 선언하지 않는다.** Pinterest 의 PIN_CLICK(클릭)이나
         * Dailymotion 의 bookmarks_total(북마크)과 달리 반응은 좋아요와 **같은 계열의
         * 상위 집합**이고, 이 엔드포인트에 좋아요만 세는 지표가 없다. 미수집으로 막으면
         * Facebook 의 유일한 참여 신호가 사라진다. 값의 성격은 여기에 남겨 둔다.
         */
        "FACEBOOK" to commonUnavailableMetrics + SHARES,
        "INSTAGRAM" to commonUnavailableMetrics,
        "TIKTOK" to commonUnavailableMetrics,
        "THREADS" to commonUnavailableMetrics,
        "TWITTER" to commonUnavailableMetrics,
        /*
         * Pinterest 는 **댓글·공유·좋아요를 모두 수집하지 않는다.**
         *
         * 요청하는 metricTypes 는 `IMPRESSION,PIN_CLICK,SAVE,VIDEO_START`
         * (`PinterestClient.kt:150`) — 댓글도 좋아요도 목록에 없다.
         *
         * - **댓글**: `:159` 가 `comments = 0` 을 하드코딩한다.
         * - **공유**: `:160` 이 `shares = metrics["PIN_CLICK"]` — PIN_CLICK 은 핀을
         *   **클릭한 횟수**다. 클릭은 공유보다 훨씬 자주 일어나 공유율이 부풀고
         *   `SHARE_SPIKE` 오판으로 이어진다.
         * - **좋아요**: `:158` 이 `likes = metrics["SAVE"]` — SAVE 는 핀을 자기 보드에
         *   담는 **저장** 행위이지 좋아요가 아니다.
         *
         * ## 왜 이름만 고치는 것으로 끝나지 않는가
         *
         * `totalLikes` 는 여러 곳에서 **플랫폼을 가로질러 합산된다**
         * (`DashboardKpi`·`getTopVideos`·`getVideoComparison`). 저장 수를 그 합계에 넣으면
         * 서로 다른 행위를 한 숫자로 더하게 된다 — 라벨을 바꿔도 합계는 여전히 틀린다.
         *
         * ## 저장 수를 살리려면
         *
         * `analytics_daily` 에 `saves` 컬럼(Flyway 마이그레이션), `PlatformAnalytics.saves`,
         * `AnalyticsSyncScheduler` 기록, jOOQ `Tables`/`Fields` 상수, DTO·프론트 타입·UI 가
         * 함께 필요하다. 값 자체는 지금도 `analytics_daily.likes` 에 계속 쌓이므로
         * **그 작업 시점에 과거 데이터를 옮길 수 있다.** 여기서는 좋아요로 **부르지만
         * 않도록** 막는다.
         */
        "PINTEREST" to commonUnavailableMetrics + COMMENTS + SHARES + LIKES,
        "LINKEDIN" to commonUnavailableMetrics,
        "WORDPRESS" to commonUnavailableMetrics + SHARES,
        /*
         * Tumblr 는 **조회수를 수집하지 않는다.**
         *
         * `TumblrClient.kt:141` 은 `views = response.response?.totalNotes` 다. `total_notes`
         * 는 그 글에 달린 **노트 총합(좋아요 + 리블로그 + 답글)** 이지 조회수가 아니다
         * (`TumblrDtos.kt:66`). 같은 응답의 노트 목록에서 좋아요·리블로그·답글을 따로 세어
         * likes·shares·comments 에 넣으므로, 참여율의 분자와 분모가 **거의 같은 수**가 된다 —
         * 모든 Tumblr 글이 참여율 100% 근처로 보인다.
         *
         * Tumblr 공개 API 에는 글 조회수가 없다. 분모가 없으므로 비율 축은 성립하지 않는다.
         */
        "TUMBLR" to commonUnavailableMetrics + VIEWS,
        "VIMEO" to commonUnavailableMetrics + SHARES,
        /*
         * Dailymotion 은 **공유를 수집하지 않는다.**
         *
         * `DailymotionClient.kt:121` 은 `shares = response.bookmarksTotal` 이고, 요청 필드는
         * `bookmarks_total`(`:113`) 이다. 북마크(즐겨찾기)는 공유가 아니다. 요청한 필드
         * 목록에 공유 수는 아예 없다.
         */
        "DAILYMOTION" to commonUnavailableMetrics + SHARES,
        /*
         * Naver Clip 은 **아무것도 수집하지 않는다.**
         *
         * `NaverClipClient.getVideoAnalytics` 는 값을 돌려주지 않고
         * `PlatformApiException("Naver Clip은 공개 업로드·관리 API를 제공하지 않습니다")`
         * 를 던진다. 그래서 이 플랫폼의 `analytics_daily` 행은 전부 컬럼 기본값 0 이다.
         *
         * 이 항목이 빠져 있어서 [forPlatform] 이 `emptySet()` 을 돌려줬고, 그것은
         * **"모든 지표를 수집한다"** 는 뜻으로 읽혔다. `Platform` enum 은 13개인데 이 맵은
         * 12개였다 — 조용히 fail-open 이던 자리다.
         */
        "NAVER_CLIP" to allMetrics,
    )

    /**
     * 알 수 없는 플랫폼은 **아무 지표도 수집하지 않는 것으로 본다.**
     *
     * `emptySet()` 을 돌려주면 호출부의 `metric !in unavailable` 이 전부 참이 되어
     * "모든 지표를 수집한다" 는 정반대 뜻이 된다. [isAvailable] 은 이미 fail-closed 이므로
     * 두 함수가 서로 다른 답을 하고 있었다.
     */
    fun forPlatform(platform: String): Set<String> =
        byPlatform[platform.uppercase()] ?: allMetrics

    /** 알 수 없는 플랫폼은 지원되는 지표라고 추정하지 않는다. */
    fun isAvailable(platform: String, metric: String): Boolean =
        byPlatform[platform.uppercase()]?.contains(metric) == false

    /**
     * 해당 지표를 실제로 수집하는 플랫폼 이름 전체.
     *
     * **가용성 판정과 합산 SQL 이 같은 근거를 쓰게 하려고 존재한다.** 예전에는 판정만
     * 플랫폼을 걸렀고 금액 합산은 상태만 봤다. 그래서 수집하지 않는 플랫폼에 `MEASURED`
     * 행이 하나라도 생기면 금액은 더해지는데 화면은 "수집하지 않습니다" 라고 말하는
     * 모순이 났다. 새 플랫폼이 수익을 지원하게 될 때 한쪽만 고치는 일도 여기서 막는다.
     */
    fun platformsReporting(metric: String): Set<String> =
        byPlatform.filterValues { !it.contains(metric) }.keys
}
