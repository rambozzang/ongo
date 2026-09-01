package com.ongo.application.analytics

import com.ongo.common.enums.Platform
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.CrossPlatformRaw
import com.ongo.domain.credit.CreditRepository
import com.ongo.domain.user.UserRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 태그 성과와 크로스 플랫폼 비교가 **미수집 지표를 실측처럼 합치지 않는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * 두 엔드포인트 모두 플랫폼 가용성 필터 없이 raw 합계를 냈다.
 *
 * - Tumblr 의 `views` 는 `total_notes`(노트 총합)다 — 조회수가 아니다.
 * - Pinterest 의 `likes` 는 `SAVE`(저장), `shares` 는 `PIN_CLICK`(클릭)이다.
 *
 * 0 이 아니라 **다른 뜻의 큰 숫자**라 합계에 섞이면 조용히 틀린다. 특히 크로스 플랫폼
 * 순위는 그 합계로 정렬하고 "가장 성과가 좋은 플랫폼" 을 뽑았다.
 */
class TagAndCrossPlatformMeasurementTest {

    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val videoRepository = mockk<VideoRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>()

    private val useCase = AnalyticsUseCase(
        analyticsRepository = analyticsRepository,
        userRepository = mockk<UserRepository>(relaxed = true),
        videoRepository = videoRepository,
        videoUploadRepository = videoUploadRepository,
        creditRepository = mockk<CreditRepository>(relaxed = true),
    )

    private val userId = 7L

    // ══ getTagPerformance ═══════════════════════════════════════════════════

    private fun givenTaggedVideo(
        platform: Platform,
        views: Int,
        likes: Int,
        previousViews: Int? = null,
    ) {
        val video = Video(id = 1L, userId = userId, title = "영상", tags = listOf("브이로그"))
        val upload = VideoUpload(id = 101L, videoId = 1L, platform = platform, channelId = 1L)
        every { videoRepository.findByUserId(userId, any(), any()) } returns listOf(video)
        every { videoUploadRepository.findByVideoIds(listOf(1L)) } returns mapOf(1L to listOf(upload))

        val today = LocalDate.now()
        val currentRows = mapOf(
            101L to listOf(AnalyticsDaily(videoUploadId = 101L, date = today, views = views, likes = likes)),
        )
        val previousRows = previousViews?.let {
            mapOf(101L to listOf(AnalyticsDaily(videoUploadId = 101L, date = today.minusDays(40), views = it)))
        } ?: emptyMap()

        // 현재 기간과 이전 기간 조회를 날짜 범위로 구분한다.
        every { analyticsRepository.findByVideoUploadIdsAndDateRange(any(), any(), any()) } answers {
            val from = secondArg<LocalDate>()
            if (from.isBefore(today.minusDays(30))) previousRows else currentRows
        }
    }

    private fun tag() = useCase.getTagPerformance(userId, 30).tags.single()

    /** **Tumblr 의 `views` 는 노트 총합이다.** 태그 조회수 합계에 들어가면 안 된다. */
    @Test
    @DisplayName("태그 성과가 Tumblr 노트 총합을 조회수로 합치지 않는다")
    fun tagPerformanceExcludesTumblrNoteCount() {
        givenTaggedVideo(Platform.TUMBLR, views = 500, likes = 60)

        val item = tag()

        assertNull(item.totalViews, "노트 총합이 조회수 합계로 나갔다")
        assertNull(item.avgViews)
        assertNull(item.avgEngagement, "노트 총합을 분모로 참여율을 계산했다")
        assertTrue(PlatformMetricAvailability.VIEWS in item.unavailableMetrics)
        // 영상 수는 실제 개수라 그대로 남는다.
        assertEquals(1, item.videoCount)
    }

    /** **Pinterest 의 `likes` 는 저장(Save) 수다.** */
    @Test
    @DisplayName("태그 성과가 Pinterest 저장 수를 좋아요로 합치지 않는다")
    fun tagPerformanceExcludesPinterestSaves() {
        givenTaggedVideo(Platform.PINTEREST, views = 500, likes = 300)

        val item = tag()

        assertNull(item.totalLikes, "저장 수가 좋아요 합계로 나갔다")
        assertNull(item.avgEngagement, "좋아요를 수집하지 않는데 참여율을 냈다")
        assertTrue(PlatformMetricAvailability.LIKES in item.unavailableMetrics)
        // Pinterest 는 노출을 조회수로 쓰므로 조회수 자체는 남는다.
        assertEquals(500L, item.totalViews)
    }

    @Test
    @DisplayName("YouTube 태그는 조회수·좋아요·참여율을 모두 계산한다")
    fun youtubeTagMeasuresEverything() {
        givenTaggedVideo(Platform.YOUTUBE, views = 1_000, likes = 100)

        val item = tag()

        assertEquals(1_000L, item.totalViews)
        assertEquals(100L, item.totalLikes)
        assertEquals(10.0, item.avgEngagement)
        assertTrue(item.unavailableMetrics.isEmpty())
    }

    /** **측정된 0 은 관측이다.** 좋아요가 실제로 0회면 참여율 0% 가 맞다. */
    @Test
    @DisplayName("측정된 0 좋아요는 참여율 0%로 보존한다")
    fun measuredZeroLikesKeepsZeroEngagement() {
        givenTaggedVideo(Platform.YOUTUBE, views = 1_000, likes = 0)

        val item = tag()

        assertEquals(0L, item.totalLikes, "실측 0 을 미측정으로 감췄다")
        assertEquals(0.0, item.avgEngagement)
    }

    /**
     * **이전 기간에 관측이 없으면 추세를 말할 수 없다.**
     *
     * 예전에는 `prevViews == 0L -> "stable"` 이라, 이전 기간에 행이 하나도 없어도
     * "변화 없음" 이라는 관측이 됐다.
     */
    @Test
    @DisplayName("이전 기간 관측이 없으면 추세를 만들지 않는다")
    fun noPreviousObservationProducesNoTrend() {
        givenTaggedVideo(Platform.YOUTUBE, views = 1_000, likes = 10, previousViews = null)

        assertNull(tag().trend, "비교할 이전 관측이 없는데 추세를 말했다")
    }

    @Test
    @DisplayName("이전 기간 관측이 있으면 추세를 계산한다")
    fun previousObservationProducesATrend() {
        givenTaggedVideo(Platform.YOUTUBE, views = 1_000, likes = 10, previousViews = 100)

        assertEquals("up", tag().trend)
    }

    // ══ getCrossPlatformComparison ══════════════════════════════════════════

    private fun raw(
        platform: String,
        views: Long,
        likes: Long = 0,
        comments: Long = 0,
        shares: Long = 0,
        videoId: Long = 1L,
    ) = CrossPlatformRaw(
        videoId = videoId,
        videoTitle = "영상 $videoId",
        platform = platform,
        videoUploadId = 100L + videoId,
        views = views,
        likes = likes,
        comments = comments,
        shares = shares,
        watchTimeSeconds = 0,
        revenueMicro = 0,
        impressions = 0,
        avgViewDurationSeconds = 0,
    )

    private fun crossPlatform(vararg rows: CrossPlatformRaw) {
        every { analyticsRepository.findCrossPlatformMetrics(userId, any()) } returns rows.toList()
    }

    /** **이 케이스가 노트 총합으로 순위를 매기던 자리다.** */
    @Test
    @DisplayName("플랫폼 순위가 Tumblr 노트 총합으로 정렬되지 않는다")
    fun rankingIgnoresTumblrNoteCount() {
        crossPlatform(
            raw("YOUTUBE", views = 1_000, likes = 50),
            raw("TUMBLR", views = 900_000, likes = 60),
        )

        val rankings = useCase.getCrossPlatformComparison(userId, 30).platformRankings

        assertNull(rankings.getValue("TUMBLR").totalViews, "노트 총합이 조회수 합계로 나갔다")
        assertNull(rankings.getValue("TUMBLR").rank, "조회수가 없는데 순위를 매겼다")
        assertEquals(1, rankings.getValue("YOUTUBE").rank, "실측 조회수 플랫폼이 1위여야 한다")
    }

    /** Pinterest 의 저장·클릭이 참여율 분자에 섞이면 안 된다. */
    @Test
    @DisplayName("플랫폼 순위 참여율이 Pinterest 저장·클릭을 더하지 않는다")
    fun rankingEngagementExcludesPinterestMappedMetrics() {
        crossPlatform(raw("PINTEREST", views = 1_000, likes = 300, comments = 0, shares = 400))

        val pinterest = useCase.getCrossPlatformComparison(userId, 30).platformRankings.getValue("PINTEREST")

        // Pinterest 는 좋아요·댓글·공유를 모두 주지 않는다 → 참여율을 낼 근거가 없다.
        assertNull(pinterest.avgEngagementRate, "저장·클릭 수로 참여율을 계산했다")
        // 노출은 실제로 조회하므로 조회수 합계와 순위는 남는다.
        assertEquals(1_000L, pinterest.totalViews)
        assertNotNull(pinterest.rank)
    }

    @Test
    @DisplayName("YouTube 순위는 실측으로 계산한다")
    fun youtubeRankingUsesMeasuredValues() {
        crossPlatform(raw("YOUTUBE", views = 1_000, likes = 50, comments = 30, shares = 20))

        val youtube = useCase.getCrossPlatformComparison(userId, 30).platformRankings.getValue("YOUTUBE")

        assertEquals(1_000L, youtube.totalViews)
        assertEquals(10.0, youtube.avgEngagementRate)
        assertEquals(1, youtube.rank)
    }

    /** 플랫폼별 상세 숫자도 미수집이면 비워야 한다 — 예전에는 raw 를 그대로 내보냈다. */
    @Test
    @DisplayName("플랫폼 상세 지표도 미수집이면 숫자를 비운다")
    fun platformDetailHidesUnavailableNumbers() {
        crossPlatform(raw("PINTEREST", views = 500, likes = 300, shares = 400))

        val metrics = useCase.getCrossPlatformComparison(userId, 30)
            .videos.single().platforms.single()

        assertNull(metrics.likes, "저장 수가 좋아요로 나갔다")
        assertNull(metrics.shares, "클릭 수가 공유로 나갔다")
        assertNull(metrics.comments)
        assertEquals(500L, metrics.views)
    }

    /**
     * **`bestPlatform` 은 측정된 참여율에서만 뽑는다.**
     *
     * `null` 을 0 으로 보면 미수집 플랫폼이 후보에 들어간다.
     */
    @Test
    @DisplayName("최고 플랫폼은 참여율이 측정된 플랫폼에서만 고른다")
    fun bestPlatformComesFromMeasuredRatesOnly() {
        crossPlatform(
            raw("YOUTUBE", views = 1_000, likes = 100, comments = 50, shares = 50),
            raw("TUMBLR", views = 900_000, likes = 60),
        )

        val best = useCase.getCrossPlatformComparison(userId, 30).videos.single().bestPlatform

        assertEquals("YOUTUBE", best, "참여율을 낼 수 없는 플랫폼을 최고로 뽑았다")
    }

    /** 측정된 참여율 0% 는 관측이다 — 후보에서 빼면 안 된다. */
    @Test
    @DisplayName("측정된 참여율 0%도 그대로 값으로 남는다")
    fun measuredZeroEngagementRateIsKept() {
        crossPlatform(raw("YOUTUBE", views = 1_000, likes = 0, comments = 0, shares = 0))

        val metrics = useCase.getCrossPlatformComparison(userId, 30)
            .videos.single().platforms.single()

        assertEquals(0.0, metrics.engagementRate, "실측 0% 를 미측정으로 감췄다")
    }
}
