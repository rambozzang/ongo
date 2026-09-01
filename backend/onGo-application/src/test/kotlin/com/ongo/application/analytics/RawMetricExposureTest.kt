package com.ongo.application.analytics

import com.ongo.common.enums.Platform
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.TrendData
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 분석 응답이 **미수집 값과 이름이 다른 지표를 실제 값처럼 내보내지 않는지** 고정한다.
 *
 * ## 세 가지 잘못된 매핑이 다시 나타나면 안 된다
 *
 * | 어댑터 | 필드 | 실제로 들어 있는 것 |
 * |---|---|---|
 * | `PinterestClient.kt:160` | `shares` | `PIN_CLICK` — 핀 **클릭 수** |
 * | `DailymotionClient.kt:121` | `shares` | `bookmarks_total` — **북마크 수** |
 * | `TumblrClient.kt:141` | `views` | `total_notes` — **노트 총합**(좋아요+리블로그+답글) |
 *
 * 하드코딩 0 과 달리 이쪽은 **0 이 아니라 큰 숫자**라 더 조용히 틀린다. 그대로 노출하면
 * 화면에는 "공유 400회", "조회수 100회" 같은 그럴듯한 성과가 뜬다.
 */
class RawMetricExposureTest {

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
    private val videoId = 11L

    private fun upload(id: Long, platform: Platform, videoId: Long = this.videoId) =
        VideoUpload(id = id, videoId = videoId, platform = platform, channelId = 1L)

    private fun daily(uploadId: Long, day: Int, views: Int = 0, likes: Int = 0, comments: Int = 0, shares: Int = 0) =
        AnalyticsDaily(
            videoUploadId = uploadId,
            date = LocalDate.of(2026, 8, day),
            views = views,
            likes = likes,
            commentsCount = comments,
            shares = shares,
        )

    private fun givenVideo(uploads: List<VideoUpload>, rows: Map<Long, List<AnalyticsDaily>>) {
        every { videoRepository.findById(videoId) } returns Video(id = videoId, userId = userId, title = "영상")
        every { videoRepository.findByIds(listOf(videoId)) } returns
            listOf(Video(id = videoId, userId = userId, title = "영상"))
        every { videoUploadRepository.findByVideoId(videoId) } returns uploads
        every { videoUploadRepository.findByVideoIds(listOf(videoId)) } returns mapOf(videoId to uploads)
        every { analyticsRepository.findByVideoUploadIdsAndDateRange(any(), any(), any()) } returns rows
    }

    // ══ getVideoAnalytics ═══════════════════════════════════════════════════

    /** **Pinterest 의 `shares` 는 PIN_CLICK(클릭 수)이다.** */
    @Test
    @DisplayName("영상 분석이 Pinterest 클릭 수를 공유로 내보내지 않는다")
    fun videoAnalyticsHidesPinterestClickCount() {
        givenVideo(
            uploads = listOf(upload(101L, Platform.PINTEREST)),
            rows = mapOf(101L to listOf(daily(101L, 20, views = 500, likes = 30, shares = 400))),
        )

        val detail = useCase.getVideoAnalytics(userId, videoId, 30).platforms.single()

        assertNull(detail.shares, "PIN_CLICK(클릭 수)이 공유로 나갔다")
        assertNull(detail.comments, "수집하지 않는 댓글이 0 으로 나갔다")
        /*
         * 좋아요도 없다. `PinterestClient.kt:158` 은 `likes = metrics["SAVE"]` 로
         * **저장(Save) 수**를 넣는데, 요청 metricTypes 에 좋아요 지표가 아예 없다.
         * 저장은 핀을 자기 보드에 담는 행위이지 좋아요가 아니다.
         */
        assertNull(detail.likes, "저장(Save) 수가 좋아요로 나갔다")
        assertTrue(PlatformMetricAvailability.SHARES in detail.unavailableMetrics)
        assertTrue(PlatformMetricAvailability.LIKES in detail.unavailableMetrics)
        // 노출(IMPRESSION)은 실제로 조회하는 값이라 그대로 남는다.
        assertEquals(500L, detail.views)
    }

    /** **Dailymotion 의 `shares` 는 bookmarks_total(북마크 수)이다.** */
    @Test
    @DisplayName("영상 분석이 Dailymotion 북마크 수를 공유로 내보내지 않는다")
    fun videoAnalyticsHidesDailymotionBookmarks() {
        givenVideo(
            uploads = listOf(upload(101L, Platform.DAILYMOTION)),
            rows = mapOf(101L to listOf(daily(101L, 20, views = 500, likes = 30, comments = 4, shares = 300))),
        )

        val detail = useCase.getVideoAnalytics(userId, videoId, 30).platforms.single()

        assertNull(detail.shares, "북마크 수가 공유로 나갔다")
        assertEquals(4L, detail.comments, "Dailymotion 댓글은 실제로 조회한다")
    }

    /**
     * **Tumblr 의 `views` 는 total_notes(노트 총합)다.**
     *
     * 조회수가 없으면 그릴 추이도 없다 — 일별 계열까지 비워야 그래프가 노트 총합을
     * 조회수로 그리지 않는다.
     */
    @Test
    @DisplayName("영상 분석이 Tumblr 노트 총합을 조회수로 내보내지 않는다")
    fun videoAnalyticsHidesTumblrNoteCount() {
        givenVideo(
            uploads = listOf(upload(101L, Platform.TUMBLR)),
            rows = mapOf(101L to listOf(daily(101L, 20, views = 100, likes = 60, comments = 20, shares = 20))),
        )

        val detail = useCase.getVideoAnalytics(userId, videoId, 30).platforms.single()

        assertNull(detail.views, "노트 총합이 조회수로 나갔다")
        assertTrue(detail.dailyData.isEmpty(), "노트 총합이 조회수 추이로 그려진다")
        assertTrue(PlatformMetricAvailability.VIEWS in detail.unavailableMetrics)
    }

    /** YouTube 는 네 지표를 모두 조회한다 — 회귀 확인. */
    @Test
    @DisplayName("영상 분석이 YouTube 지표는 그대로 내보낸다")
    fun videoAnalyticsKeepsYouTubeMetrics() {
        givenVideo(
            uploads = listOf(upload(101L, Platform.YOUTUBE)),
            rows = mapOf(101L to listOf(daily(101L, 20, views = 500, likes = 30, comments = 4, shares = 7))),
        )

        val detail = useCase.getVideoAnalytics(userId, videoId, 30).platforms.single()

        assertEquals(500L, detail.views)
        assertEquals(30L, detail.likes)
        assertEquals(4L, detail.comments)
        assertEquals(7L, detail.shares)
        assertTrue(detail.unavailableMetrics.isEmpty())
        assertEquals(1, detail.dailyData.size)
    }

    /** **지원 플랫폼의 실제 0 은 관측이다.** 감추면 실제 관찰을 잃는다. */
    @Test
    @DisplayName("YouTube 의 실제 0은 그대로 숫자로 내보낸다")
    fun videoAnalyticsPreservesMeasuredZero() {
        givenVideo(
            uploads = listOf(upload(101L, Platform.YOUTUBE)),
            rows = mapOf(101L to listOf(daily(101L, 20, views = 500, likes = 0, comments = 0, shares = 0))),
        )

        val detail = useCase.getVideoAnalytics(userId, videoId, 30).platforms.single()

        assertEquals(0L, detail.shares, "실측 0 을 측정 불가로 감췄다")
        assertTrue(detail.unavailableMetrics.isEmpty())
    }

    // ══ getVideoComparison ══════════════════════════════════════════════════

    @Test
    @DisplayName("영상 비교가 Pinterest 클릭 수를 공유 합계에 더하지 않는다")
    fun comparisonExcludesPinterestClickCount() {
        givenVideo(
            uploads = listOf(upload(101L, Platform.PINTEREST)),
            rows = mapOf(101L to listOf(daily(101L, 20, views = 500, likes = 30, shares = 400))),
        )

        val item = useCase.getVideoComparison(userId, listOf(videoId), 30).videos.single()

        assertNull(item.totalShares, "PIN_CLICK(클릭 수)이 공유 합계로 나갔다")
        assertNull(item.totalComments)
        assertTrue(PlatformMetricAvailability.SHARES in item.unavailableMetrics)
    }

    @Test
    @DisplayName("영상 비교가 Dailymotion 북마크 수를 공유 합계에 더하지 않는다")
    fun comparisonExcludesDailymotionBookmarks() {
        givenVideo(
            uploads = listOf(upload(101L, Platform.DAILYMOTION)),
            rows = mapOf(101L to listOf(daily(101L, 20, views = 500, shares = 300))),
        )

        assertNull(
            useCase.getVideoComparison(userId, listOf(videoId), 30).videos.single().totalShares,
            "북마크 수가 공유 합계로 나갔다",
        )
    }

    @Test
    @DisplayName("영상 비교가 Tumblr 노트 총합을 조회수 합계에 더하지 않는다")
    fun comparisonExcludesTumblrNoteCount() {
        givenVideo(
            uploads = listOf(upload(101L, Platform.TUMBLR)),
            rows = mapOf(101L to listOf(daily(101L, 20, views = 100, likes = 60))),
        )

        val item = useCase.getVideoComparison(userId, listOf(videoId), 30).videos.single()

        assertNull(item.totalViews, "노트 총합이 조회수 합계로 나갔다")
        assertNull(item.avgDailyViews, "조회수가 없는데 일평균을 만들었다")
        // 분모가 없으면 참여율도 성립하지 않는다.
        assertNull(item.engagementRate)
        assertTrue("engagementRate" in item.unavailableMetrics)
    }

    /**
     * 혼합 영상에서도 **각 합계는 그 지표를 수집하는 업로드의 행만** 더한다.
     *
     * 예전에는 참여율만 가용성을 봤고 `totalShares` 는 raw 였다 — 같은 응답 안에서
     * 두 숫자가 서로 다른 규칙을 쓰는 모순이 있었다.
     */
    @Test
    @DisplayName("혼합 영상에서 공유 합계는 수집 플랫폼 행만 더한다")
    fun comparisonSumsOnlyReportingUploads() {
        givenVideo(
            uploads = listOf(upload(101L, Platform.YOUTUBE), upload(102L, Platform.FACEBOOK)),
            rows = mapOf(
                101L to listOf(daily(101L, 20, views = 1_000, likes = 50, comments = 10, shares = 7)),
                102L to listOf(daily(102L, 20, views = 2_000, likes = 100, comments = 20, shares = 0)),
            ),
        )

        val item = useCase.getVideoComparison(userId, listOf(videoId), 30).videos.single()

        assertEquals(7L, item.totalShares, "Facebook 의 미수집 0 이 공유 합계에 섞였다")
        // 조회수·좋아요·댓글은 두 플랫폼 모두 수집한다.
        assertEquals(3_000L, item.totalViews)
        assertEquals(150L, item.totalLikes)
        assertEquals(30L, item.totalComments)
        // 공유를 못 주는 업로드가 섞였으므로 참여율은 불완전하다고 알린다.
        assertTrue("engagementRate" in item.unavailableMetrics)
    }

    // ══ getTopVideos ════════════════════════════════════════════════════════

    @Test
    @DisplayName("인기 영상이 Tumblr 노트 총합을 조회수로 내보내지 않는다")
    fun topVideosExcludeTumblrNoteCount() {
        every { analyticsRepository.getTopVideos(userId, 30, 5) } returns
            listOf(Video(id = videoId, userId = userId, title = "영상"))
        every { videoUploadRepository.findByVideoIds(listOf(videoId)) } returns
            mapOf(videoId to listOf(upload(101L, Platform.TUMBLR)))
        every { analyticsRepository.findByVideoUploadIdsAndDateRange(any(), any(), any()) } returns
            mapOf(101L to listOf(daily(101L, 20, views = 100, likes = 60)))

        val item = useCase.getTopVideos(userId, 30, 5).videos.single()

        assertNull(item.totalViews, "노트 총합이 조회수로 나갔다")
        assertTrue(PlatformMetricAvailability.VIEWS in item.unavailableMetrics)
        // Tumblr 좋아요는 노트 목록에서 실제로 센 값이라 그대로 남는다.
        assertEquals(60L, item.totalLikes)
    }

    // ══ getTrends ═══════════════════════════════════════════════════════════

    /**
     * **`subscriber_gained` 를 채우는 어댑터는 YouTube 하나뿐이다.**
     *
     * 예전에는 나머지 플랫폼의 하드코딩 0 이 합계에 들어가고 `platformSubscribers` 에도
     * 플랫폼마다 "+0" 이 실려, 화면이 "신규 구독 0명" 을 성과로 그렸다.
     */
    @Test
    @DisplayName("추세가 구독을 수집하지 않는 플랫폼의 0을 더하지 않는다")
    fun trendsExcludeNonReportingSubscriberRows() {
        every { analyticsRepository.getTrendData(userId, 30) } returns listOf(
            TrendData(LocalDate.of(2026, 8, 20), Platform.YOUTUBE, views = 1_000, subscribers = 12),
            TrendData(LocalDate.of(2026, 8, 20), Platform.TIKTOK, views = 2_000, subscribers = 0),
        )

        val point = useCase.getTrends(userId, 30).data.single()

        assertEquals(12L, point.totalSubscribers, "TikTok 의 하드코딩 0 이 섞였다")
        assertEquals(mapOf("YOUTUBE" to 12L), point.platformSubscribers, "미수집 플랫폼이 +0 으로 실렸다")
        // 조회수는 두 플랫폼 모두 수집한다.
        assertEquals(3_000L, point.totalViews)
        assertEquals(mapOf("YOUTUBE" to 1_000L, "TIKTOK" to 2_000L), point.platformViews)
    }

    @Test
    @DisplayName("구독을 수집하는 플랫폼이 없으면 합계를 만들지 않는다")
    fun trendsWithoutAnySubscriberPlatformReportNull() {
        every { analyticsRepository.getTrendData(userId, 30) } returns listOf(
            TrendData(LocalDate.of(2026, 8, 20), Platform.TIKTOK, views = 2_000, subscribers = 0),
        )

        val point = useCase.getTrends(userId, 30).data.single()

        assertNull(point.totalSubscribers, "구독 0명을 성과로 내보냈다")
        assertTrue(point.platformSubscribers.isEmpty())
        assertTrue(PlatformMetricAvailability.SUBSCRIBER_GAINED in point.unavailableMetrics)
    }

    /** Tumblr 의 노트 총합은 조회수 추세에도 들어가면 안 된다. */
    @Test
    @DisplayName("추세가 Tumblr 노트 총합을 조회수로 더하지 않는다")
    fun trendsExcludeTumblrNoteCount() {
        every { analyticsRepository.getTrendData(userId, 30) } returns listOf(
            TrendData(LocalDate.of(2026, 8, 20), Platform.YOUTUBE, views = 1_000, subscribers = 5),
            TrendData(LocalDate.of(2026, 8, 20), Platform.TUMBLR, views = 90_000, subscribers = 0),
        )

        val point = useCase.getTrends(userId, 30).data.single()

        assertEquals(1_000L, point.totalViews, "노트 총합이 조회수 합계에 섞였다")
        assertTrue("TUMBLR" !in point.platformViews)
    }

    // ══ 지원하지만 기간 내 행이 없을 때 ═════════════════════════════════════
    //
    // 여기까지는 "플랫폼이 그 지표를 주는가" 만 봤다. 그런데 지원하는 지표라도
    // **그 기간에 집계 행이 하나도 없으면** `dailyData.sumOf { .. }` 가 `0` 을 냈다.
    // 화면은 `dailyData.length` 로 숨길 수 있었지만 **JSON 계약 자체가 "0 회 측정됨"**
    // 이라고 말했고, 공개 API 소비자에게는 그게 전부였다.

    /** **(a) 이 케이스가 동기화 전 영상을 "조회수 0회" 로 내보내던 자리다.** */
    @Test
    @DisplayName("지원 플랫폼이지만 기간 행이 없으면 지표가 0 이 아니라 null 이다")
    fun videoAnalyticsPendingIsNullNotZero() {
        givenVideo(uploads = listOf(upload(101L, Platform.YOUTUBE)), rows = emptyMap())

        val detail = useCase.getVideoAnalytics(userId, videoId, 30).platforms.single()

        assertNull(detail.views, "수집 전 상태를 0 으로 위장했다")
        assertNull(detail.likes)
        assertNull(detail.comments)
        assertNull(detail.shares)
        // YouTube 는 네 지표를 모두 준다 — 미지원으로 표시하면 안 된다.
        assertTrue(
            detail.unavailableMetrics.isEmpty(),
            "수집 대기를 플랫폼 미지원으로 알렸다: ${detail.unavailableMetrics}",
        )
        // 그릴 계열도 없다.
        assertTrue(detail.dailyData.isEmpty())
    }

    /** **(b) 행이 있고 합이 0 이면 그 0 은 관측이다.** */
    @Test
    @DisplayName("행이 있고 합이 0 이면 0 을 그대로 유지한다")
    fun videoAnalyticsKeepsRealZeroWhenRowsExist() {
        givenVideo(
            uploads = listOf(upload(101L, Platform.YOUTUBE)),
            rows = mapOf(101L to listOf(daily(101L, 20, views = 0, likes = 0, comments = 0, shares = 0))),
        )

        val detail = useCase.getVideoAnalytics(userId, videoId, 30).platforms.single()

        assertEquals(0L, detail.views, "실측 0 을 미측정으로 감췄다")
        assertEquals(0L, detail.likes)
        assertEquals(0L, detail.comments)
        assertEquals(0L, detail.shares)
        assertTrue(detail.unavailableMetrics.isEmpty())
        assertEquals(1, detail.dailyData.size, "측정된 날짜를 지웠다")
    }

    /** **(c) 미지원 지표는 기존대로 null + `unavailableMetrics`, 가짜 계열도 없다.** */
    @Test
    @DisplayName("미지원 플랫폼은 null 과 사유를 유지하고 가짜 일별 계열을 만들지 않는다")
    fun videoAnalyticsUnsupportedKeepsReasonAndNoFakeSeries() {
        givenVideo(
            uploads = listOf(upload(101L, Platform.TUMBLR)),
            rows = mapOf(101L to listOf(daily(101L, 20, views = 900_000, likes = 5))),
        )

        val detail = useCase.getVideoAnalytics(userId, videoId, 30).platforms.single()

        assertNull(detail.views, "노트 총합을 조회수로 내보냈다")
        assertTrue(
            PlatformMetricAvailability.VIEWS in detail.unavailableMetrics,
            "미지원 사유를 알리지 않았다",
        )
        // 조회수를 주지 않는 플랫폼은 일별 계열도 만들지 않는다 — 그 계열이 곧 조회수 그래프다.
        assertTrue(detail.dailyData.isEmpty(), "노트 총합으로 일별 계열을 만들었다")
    }

    /**
     * **(d) 한 영상에 미지원 플랫폼과 수집 대기 플랫폼이 섞인 경우.**
     *
     * 두 상세 모두 `views = null` 이지만 **이유가 다르고 그 차이가 응답에 드러나야 한다.**
     */
    @Test
    @DisplayName("혼합 플랫폼에서 미지원과 수집 대기를 구분해서 낸다")
    fun videoAnalyticsSeparatesUnsupportedFromPending() {
        givenVideo(
            uploads = listOf(
                upload(101L, Platform.YOUTUBE),
                upload(102L, Platform.TUMBLR),
                upload(103L, Platform.TIKTOK),
            ),
            // TikTok 만 실제로 수집됐다. YouTube 는 수집 대기, Tumblr 는 미지원.
            rows = mapOf(103L to listOf(daily(103L, 20, views = 700, likes = 20))),
        )

        val details = useCase.getVideoAnalytics(userId, videoId, 30).platforms
            .associateBy { it.platform }

        // 측정된 플랫폼은 그대로 값을 낸다.
        assertEquals(700L, details.getValue(Platform.TIKTOK).views)

        // 수집 대기: null 이지만 미지원은 아니다.
        val youtube = details.getValue(Platform.YOUTUBE)
        assertNull(youtube.views)
        assertTrue(youtube.unavailableMetrics.isEmpty(), "수집 대기를 미지원으로 알렸다")

        // 미지원: null 이고 사유가 있다.
        val tumblr = details.getValue(Platform.TUMBLR)
        assertNull(tumblr.views)
        assertTrue(PlatformMetricAvailability.VIEWS in tumblr.unavailableMetrics)
    }

    /**
     * 공개 API 는 이 응답을 그대로 읽는다. 수집 대기 상태에서 지표를 만들어 내면
     * 외부 소비자가 그것을 측정값으로 그린다.
     */
    @Test
    @DisplayName("수집 대기 상태에서 일별 계열이 비어 공개 API 가 지표를 만들지 않는다")
    fun pendingProducesNoDailySeriesForPublicApi() {
        givenVideo(uploads = listOf(upload(101L, Platform.YOUTUBE)), rows = emptyMap())

        val platforms = useCase.getVideoAnalytics(userId, videoId, 30).platforms

        assertTrue(platforms.all { it.dailyData.isEmpty() }, "없는 일별 계열을 만들었다")
    }

    /** 조회수를 수집하는 행이 없는 날짜는 점 자체를 만들지 않는다. */
    @Test
    @DisplayName("조회수 표본이 없는 날짜는 추세 점을 만들지 않는다")
    fun trendsSkipDatesWithoutAnyViewSample() {
        every { analyticsRepository.getTrendData(userId, 30) } returns listOf(
            TrendData(LocalDate.of(2026, 8, 20), Platform.TUMBLR, views = 90_000, subscribers = 0),
        )

        assertTrue(useCase.getTrends(userId, 30).data.isEmpty(), "노트 총합으로 추세 점을 만들었다")
    }
}
