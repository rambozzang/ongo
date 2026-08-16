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

    private val commonVideoDurationMetrics = setOf(WATCH_TIME_SECONDS, AVG_VIEW_DURATION)

    private val byPlatform = mapOf(
        "FACEBOOK" to commonVideoDurationMetrics + SHARES,
        "INSTAGRAM" to commonVideoDurationMetrics,
        "TIKTOK" to commonVideoDurationMetrics,
        "THREADS" to commonVideoDurationMetrics,
        "TWITTER" to commonVideoDurationMetrics,
        "PINTEREST" to commonVideoDurationMetrics + COMMENTS,
        "LINKEDIN" to commonVideoDurationMetrics,
        "WORDPRESS" to commonVideoDurationMetrics + SHARES,
        "TUMBLR" to commonVideoDurationMetrics,
        "VIMEO" to commonVideoDurationMetrics + SHARES,
        "DAILYMOTION" to commonVideoDurationMetrics,
    )

    fun forPlatform(platform: String): Set<String> =
        byPlatform[platform.uppercase()] ?: emptySet()
}
