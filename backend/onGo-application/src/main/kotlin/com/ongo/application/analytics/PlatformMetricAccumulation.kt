package com.ongo.application.analytics

/**
 * 플랫폼이 돌려주는 참여 지표가 **그 기간의 증분인지, 평생 누적인지**를 정한다.
 *
 * ## 왜 이 구분이 필요한가
 *
 * `AnalyticsSyncScheduler` 는 어댑터가 준 숫자를 `analytics_daily` 의 **그 날짜 행**에
 * 그대로 넣는다. 그리고 화면은 그 행들을 기간으로 `SUM` 한다. 이 두 동작은 저장된 값이
 * "그 날 하루의 증가분" 일 때만 맞는다.
 *
 * 그런데 어댑터 13개 중 **YouTube 하나만** 그 조건을 만족한다. YouTube 는 Analytics API
 * 에 `startDate`/`endDate` 를 넘기고 그 기간의 값을 받는다. 나머지 12개는 시그니처로
 * 날짜를 받기만 하고 **평생 누적 카운터**를 돌려준다.
 *
 * ```
 * TikTok       video.view_count            평생 조회수
 * Instagram    insights "plays"            평생 재생수
 * Twitter      public_metrics.*_count      평생
 * Facebook     total_video_views 등        평생 ("total_" 접두사)
 * LinkedIn     totalShareStatistics        평생
 * Threads      media insights              평생
 * Tumblr       total_notes                 평생
 * Vimeo        plays / *.total             평생
 * Dailymotion  views_total 등              평생 ("_total" 접미사)
 * WordPress    post stats                  평생
 * Pinterest    all.lifetime_metrics        평생 ← 날짜를 넘기고도 lifetime 을 읽는다
 * ```
 *
 * Pinterest 가 특히 함정이다. `startDate`/`endDate` 를 API 에 **실제로 전달하지만**
 * 응답에서 꺼내는 필드가 `lifetime_metrics` 다. 호출부만 보면 기간별로 보인다.
 *
 * ## 그래서 무슨 일이 벌어졌나
 *
 * 평생 누적값이 날짜별 행에 반복 저장되고, 대시보드가 그것을 기간 합산했다.
 * 30일 창이면 조회수가 **약 30배**로 나온다. 크리에이터가 처음 보는 숫자다.
 *
 * ## 이 계약을 쓰는 쪽
 *
 * [com.ongo.application.analytics.AnalyticsSyncScheduler] 가 누적 플랫폼의 값을
 * **이전 스냅샷과의 차분**으로 바꿔 저장한다. 그래야 집계가 지금처럼 `SUM` 으로 남는다.
 *
 * @see PlatformMetricAvailability 지표를 **아예 수집하지 않는** 경우의 계약. 이쪽은
 *   수집은 하되 **의미가 다른** 경우를 다룬다. 둘은 독립이다 — Pinterest 처럼 누적이면서
 *   일부 지표는 미수집인 조합이 있다.
 */
object PlatformMetricAccumulation {

    /** 저장된 참여 지표가 무엇을 뜻하는지. */
    enum class Basis {
        /** 그 날짜 하루의 증가분. `SUM` 해도 된다. */
        INCREMENTAL,

        /** 관측 시점의 평생 누적 카운터. 그대로 `SUM` 하면 안 된다. */
        CUMULATIVE,
    }

    /**
     * 기간 파라미터를 실제로 반영해 **그 기간의 값**을 돌려주는 플랫폼.
     *
     * 지금은 YouTube 뿐이다. 새 어댑터를 넣을 때는 "날짜를 넘기는가" 가 아니라
     * **"응답 필드가 기간값인가"** 를 보고 판단할 것 — Pinterest 가 그 둘이 다른 예다.
     */
    private val incrementalPlatforms = setOf("YOUTUBE")

    /**
     * 알 수 없는 플랫폼은 **누적으로 본다.**
     *
     * 증분으로 추정하면 그 값이 곧바로 합계에 들어가 화면의 숫자를 부풀린다.
     * 누적으로 보면 최악의 경우 첫 관측이 기준선으로 빠질 뿐이다 — 틀린 숫자를
     * 보여주는 것보다 언제나 낫다.
     */
    fun basisFor(platform: String): Basis =
        if (platform.uppercase() in incrementalPlatforms) Basis.INCREMENTAL else Basis.CUMULATIVE

    fun isCumulative(platform: String): Boolean = basisFor(platform) == Basis.CUMULATIVE

    /** 기간값을 그대로 쓸 수 있는 플랫폼 이름 전체. 계약을 읽는 쪽이 목록을 복사하지 않게 한다. */
    fun incrementalPlatformNames(): Set<String> = incrementalPlatforms
}
