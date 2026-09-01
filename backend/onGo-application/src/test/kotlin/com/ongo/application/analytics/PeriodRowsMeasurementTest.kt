package com.ongo.application.analytics

import com.ongo.common.enums.Platform
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 세 집계 API 가 **"기간 내 행 없음" 을 실측 0 으로 위장하지 않는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * if (viewUploads.isEmpty()) null else rowsOf(viewUploads).sumOf { it.views.toLong() }
 * ```
 *
 * 앞의 `isEmpty()` 는 **플랫폼 계약**만 본다. 업로드가 있으면 그 뒤로 넘어가는데,
 * `emptyList().sumOf { .. }` 는 `0` 이다. 그래서 YouTube 에 올렸지만 **그 기간에 아직
 * 동기화되지 않은** 영상이 "조회수 0회" 로 나갔다 — 실제로 0 회였던 영상과 같은 모양이다.
 *
 * ## 지금의 계약 (새 필드 없이 구분된다)
 *
 * | 상태 | 값 | `unavailableMetrics` |
 * |---|---|---|
 * | 그 지표를 주는 업로드가 없다 | `null` | 지표 **포함** |
 * | 수집하지만 기간 내 행이 없다 | `null` | 지표 **미포함** |
 * | 행이 있고 합이 0 | `0` | 미포함 |
 *
 * `unavailableMetrics` 의 의미(플랫폼 계약)는 그대로라 기존 클라이언트가 깨지지 않는다.
 */
class PeriodRowsMeasurementTest {

    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val videoRepository = mockk<VideoRepository>(relaxed = true)
    private val videoUploadRepository = mockk<VideoUploadRepository>()

    private val useCase = AnalyticsUseCase(
        analyticsRepository = analyticsRepository,
        userRepository = mockk(relaxed = true),
        videoRepository = videoRepository,
        videoUploadRepository = videoUploadRepository,
        creditRepository = mockk(relaxed = true),
    )

    private val userId = 7L
    private val videoId = 1L

    private fun upload(id: Long, platform: Platform) =
        VideoUpload(id = id, videoId = videoId, platform = platform)

    private fun row(
        uploadId: Long,
        views: Int = 0,
        likes: Int = 0,
        comments: Int = 0,
        shares: Int = 0,
        watchTime: Long = 0,
    ) = AnalyticsDaily(
        videoUploadId = uploadId,
        date = LocalDate.now().minusDays(1),
        views = views,
        likes = likes,
        commentsCount = comments,
        shares = shares,
        watchTimeSeconds = watchTime,
    )

    /** 세 API 가 모두 같은 업로드·행 구성을 보도록 스텁한다. */
    private fun given(uploads: List<VideoUpload>, rows: List<AnalyticsDaily>, tags: List<String> = listOf("브이로그")) {
        val video = Video(id = videoId, userId = userId, title = "영상", tags = tags)
        every { analyticsRepository.getTopVideos(userId, any(), any()) } returns listOf(video)
        every { videoRepository.findByUserId(userId, any(), any()) } returns listOf(video)
        every { videoRepository.findByIds(listOf(videoId)) } returns listOf(video)
        every { videoUploadRepository.findByVideoIds(listOf(videoId)) } returns mapOf(videoId to uploads)
        every { analyticsRepository.findByVideoUploadIdsAndDateRange(any(), any(), any()) } returns
            rows.groupBy { it.videoUploadId }
    }

    private fun topVideo() = useCase.getTopVideos(userId, 30, 5).videos.single()
    private fun tag() = useCase.getTagPerformance(userId, 30).tags.single()
    private fun compared() = useCase.getVideoComparison(userId, listOf(videoId), 30).videos.single()

    // ══ A) Top videos ═══════════════════════════════════════════════════════

    /** **이 케이스가 동기화 전 영상을 "조회수 0회" 로 내보내던 자리다.** */
    @Test
    @DisplayName("A: 인기 영상 — 지원 플랫폼이지만 기간 행이 없으면 null")
    fun topVideosPendingIsNull() {
        given(uploads = listOf(upload(11L, Platform.YOUTUBE)), rows = emptyList())

        val item = topVideo()

        assertNull(item.totalViews, "수집 전 상태를 0 으로 위장했다")
        assertNull(item.totalLikes)
        // 플랫폼은 그 지표를 준다 — 미지원과 섞지 않는다.
        assertTrue(item.unavailableMetrics.isEmpty())
    }

    /** **행이 있고 합이 0 이면 그 0 은 관측이다.** */
    @Test
    @DisplayName("A: 인기 영상 — 실측 0 행은 0 으로 보존")
    fun topVideosMeasuredZeroStaysZero() {
        given(
            uploads = listOf(upload(11L, Platform.YOUTUBE)),
            rows = listOf(row(11L, views = 0, likes = 0)),
        )

        val item = topVideo()

        assertEquals(0L, item.totalViews, "실측 0 을 미측정으로 감췄다")
        assertEquals(0L, item.totalLikes)
        assertTrue(item.unavailableMetrics.isEmpty())
    }

    // ══ B) Tag performance ══════════════════════════════════════════════════

    @Test
    @DisplayName("B: 태그 — 지원 플랫폼이지만 기간 행이 없으면 null")
    fun tagPendingIsNull() {
        given(uploads = listOf(upload(11L, Platform.YOUTUBE)), rows = emptyList())

        val item = tag()

        assertNull(item.totalViews, "수집 전 상태를 0 으로 위장했다")
        assertNull(item.totalLikes)
        assertNull(item.avgViews, "미측정에서 평균을 만들었다")
        assertTrue(item.unavailableMetrics.isEmpty())
        // 영상 수는 실제 개수라 그대로다.
        assertEquals(1, item.videoCount)
    }

    @Test
    @DisplayName("B: 태그 — 실측 0 행은 0 으로 보존")
    fun tagMeasuredZeroStaysZero() {
        given(
            uploads = listOf(upload(11L, Platform.YOUTUBE)),
            rows = listOf(row(11L, views = 0, likes = 0)),
        )

        val item = tag()

        assertEquals(0L, item.totalViews, "실측 0 을 미측정으로 감췄다")
        assertEquals(0L, item.totalLikes)
        assertEquals(0L, item.avgViews)
    }

    // ══ C) Video comparison ═════════════════════════════════════════════════

    @Test
    @DisplayName("C: 비교 — 지원 플랫폼이지만 기간 행이 없으면 지표가 전부 null")
    fun comparisonPendingIsNull() {
        given(uploads = listOf(upload(11L, Platform.YOUTUBE)), rows = emptyList())

        val item = compared()

        assertNull(item.totalViews, "수집 전 상태를 0 으로 위장했다")
        assertNull(item.totalLikes)
        assertNull(item.totalComments)
        assertNull(item.totalShares)
        assertNull(item.totalWatchTimeSeconds)
        assertNull(item.avgDailyViews)
        // 분모가 없으므로 참여율도 성립하지 않는다.
        assertNull(item.engagementRate)
        // YouTube 는 다섯 지표를 모두 준다 — 미지원으로 표시하면 안 된다.
        assertFalse(PlatformMetricAvailability.VIEWS in item.unavailableMetrics)
        assertFalse(PlatformMetricAvailability.WATCH_TIME_SECONDS in item.unavailableMetrics)
    }

    @Test
    @DisplayName("C: 비교 — 실측 0 행은 0 으로 보존")
    fun comparisonMeasuredZeroStaysZero() {
        given(
            uploads = listOf(upload(11L, Platform.YOUTUBE)),
            rows = listOf(row(11L, views = 0, likes = 0, comments = 0, shares = 0, watchTime = 0)),
        )

        val item = compared()

        assertEquals(0L, item.totalViews, "실측 0 을 미측정으로 감췄다")
        assertEquals(0L, item.totalLikes)
        assertEquals(0L, item.totalComments)
        assertEquals(0L, item.totalShares)
        assertEquals(0L, item.totalWatchTimeSeconds)
    }

    /** 조회수 행이 있어도 합이 0 이면 참여율의 분모가 없다 — 0.0% 는 관측이 아니다. */
    @Test
    @DisplayName("C: 비교 — 조회수 실측 0 이면 참여율은 만들지 않는다")
    fun comparisonZeroViewsProduceNoEngagementRate() {
        given(
            uploads = listOf(upload(11L, Platform.YOUTUBE)),
            rows = listOf(row(11L, views = 0, likes = 5)),
        )

        val item = compared()

        assertEquals(0L, item.totalViews)
        assertNull(item.engagementRate, "분모 없는 비율을 만들었다")
    }

    // ══ D) 혼합 — unsupported 와 pending 을 섞지 않는다 ═════════════════════

    /**
     * Tumblr 는 조회수를 주지 않고(`total_notes`), YouTube 는 준다.
     * YouTube 행만 있으면 그 값이 그대로 남고, Tumblr 가 주지 않는 지표만 미지원이다.
     */
    @Test
    @DisplayName("D: 혼합 — 미지원 지표와 측정값을 함께 정확히 낸다")
    fun mixedPlatformsSeparateUnsupportedFromMeasured() {
        given(
            uploads = listOf(upload(11L, Platform.YOUTUBE), upload(12L, Platform.PINTEREST)),
            rows = listOf(row(11L, views = 500, likes = 30, comments = 4)),
        )

        val item = compared()

        // YouTube 행의 실측이 살아 있다.
        assertEquals(500L, item.totalViews)
        assertEquals(30L, item.totalLikes)
        // Pinterest 는 댓글을 주지 않지만 YouTube 는 준다 → 물어볼 곳이 있으므로 측정된다.
        assertEquals(4L, item.totalComments)
        assertFalse(PlatformMetricAvailability.VIEWS in item.unavailableMetrics)
    }

    /**
     * **미지원과 수집 대기가 한 응답 안에 함께 있는 경우.**
     *
     * Tumblr 만 있으면 조회수는 미지원(`unavailableMetrics` 포함)이고, 같은 영상의
     * 좋아요는 Tumblr 가 주므로 미지원이 아니지만 행이 없어 수집 대기다. 둘 다 값은
     * `null` 이지만 **이유가 다르고 그 차이가 응답에 드러나야 한다.**
     */
    @Test
    @DisplayName("D: 혼합 — 미지원과 수집 대기를 같은 null 로 뭉치지 않는다")
    fun unsupportedAndPendingAreDistinguishable() {
        given(uploads = listOf(upload(11L, Platform.TUMBLR)), rows = emptyList())

        val item = compared()

        assertNull(item.totalViews)
        assertNull(item.totalLikes)
        // 조회수는 Tumblr 가 주지 않는다 → 미지원.
        assertTrue(
            PlatformMetricAvailability.VIEWS in item.unavailableMetrics,
            "미지원 지표를 알리지 않았다",
        )
        // 좋아요는 Tumblr 가 준다 → 미지원이 아니라 수집 대기.
        assertFalse(
            PlatformMetricAvailability.LIKES in item.unavailableMetrics,
            "수집 대기를 플랫폼 미지원으로 알렸다",
        )
    }

    /** 미지원 플랫폼의 행이 있어도 그 지표는 여전히 미지원이고 값에 섞이지 않는다. */
    @Test
    @DisplayName("D: 혼합 — 미지원 플랫폼 행은 값에 섞이지 않는다")
    fun unsupportedRowsNeverEnterTheSum() {
        given(
            uploads = listOf(upload(11L, Platform.YOUTUBE), upload(12L, Platform.TUMBLR)),
            rows = listOf(row(11L, views = 400), row(12L, views = 900_000)),
        )

        assertEquals(400L, topVideo().totalViews, "노트 총합이 조회수에 섞였다")
        assertEquals(400L, tag().totalViews)
        assertEquals(400L, compared().totalViews)
    }

    // ══ E) 순위·정렬이 null 을 0 으로 만들지 않는다 ═════════════════════════

    /**
     * 태그 정렬은 `totalViews` 내림차순이다. 미측정 태그를 `0` 으로 바꿔 끼우면
     * 실측 0 인 태그와 순서가 섞인다. 미측정은 **뒤로** 가야 한다.
     */
    @Test
    @DisplayName("E: 태그 정렬 — 미측정은 실측 0 보다 뒤로 간다")
    fun unmeasuredTagsSortBehindMeasuredZero() {
        val measured = Video(id = 1L, userId = userId, title = "측정", tags = listOf("측정됨"))
        val pending = Video(id = 2L, userId = userId, title = "대기", tags = listOf("수집대기"))
        every { videoRepository.findByUserId(userId, any(), any()) } returns listOf(measured, pending)
        every { videoUploadRepository.findByVideoIds(any()) } returns mapOf(
            1L to listOf(VideoUpload(id = 11L, videoId = 1L, platform = Platform.YOUTUBE)),
            2L to listOf(VideoUpload(id = 12L, videoId = 2L, platform = Platform.YOUTUBE)),
        )
        // 1 번 영상만 실측 0 행이 있다.
        every { analyticsRepository.findByVideoUploadIdsAndDateRange(any(), any(), any()) } returns
            mapOf(11L to listOf(row(11L, views = 0)))

        val tags = useCase.getTagPerformance(userId, 30).tags

        assertEquals("측정됨", tags.first().tag, "미측정 태그가 실측 0 보다 앞섰다")
        assertEquals(0L, tags.first().totalViews)
        assertNull(tags.last().totalViews)
    }
}
