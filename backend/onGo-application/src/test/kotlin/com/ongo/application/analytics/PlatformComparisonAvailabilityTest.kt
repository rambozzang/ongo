package com.ongo.application.analytics

import com.ongo.common.enums.Platform
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.CrossPlatformDetailRaw
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 플랫폼 비교가 **그 플랫폼이 주지 않는 지표를 숫자로 내리지 않는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * likes = data.sumOf { it.likes },
 * comments = data.sumOf { it.comments },
 * shares = data.sumOf { it.shares },
 * ```
 *
 * Facebook·WordPress·Vimeo 는 공유 수를, Pinterest 는 댓글 수를 API 로 주지 않는다
 * ([PlatformMetricAvailability]). 저장된 0 은 측정값이 아니라 자리 채우기인데 그대로
 * 합산돼, 플랫폼 비교 화면이 **"Facebook 공유 0회"** 를 성과처럼 보여줬다.
 *
 * 같은 클래스의 `getVideoComparison` 은 이미 `unavailableMetrics` 계약을 쓴다 —
 * 형제 메서드 사이의 불일치였다.
 */
class PlatformComparisonAvailabilityTest {

    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val useCase = AnalyticsUseCase(
        analyticsRepository = analyticsRepository,
        userRepository = mockk(relaxed = true),
        videoRepository = mockk(relaxed = true),
        videoUploadRepository = mockk(relaxed = true),
        creditRepository = mockk(relaxed = true),
    )

    private val userId = 7L

    private fun row(
        platform: String,
        uploadId: Long,
        views: Long = 100,
        likes: Long = 10,
        comments: Long = 5,
        shares: Long = 3,
    ) = CrossPlatformDetailRaw(
        videoId = uploadId, videoTitle = "v$uploadId", thumbnailUrls = emptyList(), publishedAt = null,
        platform = platform, videoUploadId = uploadId,
        views = views, likes = likes, comments = comments, shares = shares,
        watchTimeSeconds = 0, revenueMicro = 0, impressions = 0, avgViewDurationSeconds = 0,
    )

    private fun given(vararg rows: CrossPlatformDetailRaw) {
        every { analyticsRepository.findCrossPlatformDetailMetrics(userId, any()) } returns rows.toList()
    }

    private fun summaryOf(platform: Platform) =
        useCase.getPlatformComparison(userId, 30).platforms.single { it.platform == platform }

    // ── 미지원 지표는 null ───────────────────────────────────────────────────

    /** **이 케이스가 "Facebook 공유 0회" 를 만들던 자리다.** */
    @Test
    @DisplayName("Facebook 공유는 null이고 미지원 목록에 담긴다")
    fun facebookSharesAreUnavailable() {
        given(row("FACEBOOK", 1, views = 500, likes = 40, comments = 12, shares = 0))

        val summary = summaryOf(Platform.FACEBOOK)

        assertNull(summary.shares, "미수집 공유가 0 으로 나갔다")
        assertTrue(PlatformMetricAvailability.SHARES in summary.unavailableMetrics)
        // 지원하는 지표는 그대로 살아 있어야 한다.
        assertEquals(500L, summary.views)
        assertEquals(40L, summary.likes)
        assertEquals(12L, summary.comments)
    }

    /**
     * Pinterest 는 댓글도 공유도 주지 않는다.
     *
     * 공유 자리에 있던 값은 `PinterestClient.kt:160` 의 `metrics["PIN_CLICK"]` 이다.
     * PIN_CLICK 은 **핀을 클릭한 횟수**이지 공유가 아니다 — 이름이 다른 지표를 공유로
     * 쓰고 있었다. 클릭은 공유보다 훨씬 자주 일어나므로 그대로 두면 공유율이 부풀었다.
     */
    @Test
    @DisplayName("Pinterest 댓글과 공유는 null이고 미지원 목록에 담긴다")
    fun pinterestCommentsAndSharesAreUnavailable() {
        given(row("PINTEREST", 1, views = 300, likes = 20, comments = 0, shares = 7))

        val summary = summaryOf(Platform.PINTEREST)

        assertNull(summary.comments, "미수집 댓글이 0 으로 나갔다")
        assertTrue(PlatformMetricAvailability.COMMENTS in summary.unavailableMetrics)
        assertNull(summary.shares, "PIN_CLICK(클릭 수)이 공유 수로 나갔다")
        assertTrue(PlatformMetricAvailability.SHARES in summary.unavailableMetrics)
        /*
         * 좋아요도 없다. `PinterestClient.kt:158` 은 `likes = metrics["SAVE"]` — **저장 수**다.
         * `totalLikes` 는 플랫폼을 가로질러 합산되므로, 라벨만 고쳐서는 합계가 여전히
         * 서로 다른 행위를 더하게 된다.
         */
        assertNull(summary.likes, "저장(Save) 수가 좋아요로 나갔다")
        assertTrue(PlatformMetricAvailability.LIKES in summary.unavailableMetrics)
        // 노출(IMPRESSION)은 실제로 조회하는 값이라 그대로 남는다.
        assertEquals(300L, summary.views)
    }

    /** 플랫폼마다 빠지는 지표가 다르다. 한 플랫폼 규칙을 전체에 적용하면 안 된다. */
    @Test
    @DisplayName("플랫폼마다 다른 미지원 지표를 각각 반영한다")
    fun perPlatformUnavailabilityIsRespected() {
        given(
            row("FACEBOOK", 1, shares = 0),
            row("PINTEREST", 2, comments = 0),
            row("YOUTUBE", 3),
        )

        val response = useCase.getPlatformComparison(userId, 30)

        assertNull(response.platforms.single { it.platform == Platform.FACEBOOK }.shares)
        assertNull(response.platforms.single { it.platform == Platform.PINTEREST }.comments)
        val youtube = response.platforms.single { it.platform == Platform.YOUTUBE }
        assertTrue(youtube.unavailableMetrics.isEmpty(), "YouTube 는 세 지표를 모두 준다")
    }

    // ── 지원 지표의 실제 0 은 보존 ───────────────────────────────────────────

    /** **지원하는 지표의 0 은 관측 결과다.** null 로 감추면 실제 관찰을 잃는다. */
    @Test
    @DisplayName("YouTube의 실제 0은 그대로 보존한다")
    fun youtubeMeasuredZerosArePreserved() {
        given(row("YOUTUBE", 1, views = 1000, likes = 0, comments = 0, shares = 0))

        val summary = summaryOf(Platform.YOUTUBE)

        assertEquals(0L, summary.likes)
        assertEquals(0L, summary.comments)
        assertEquals(0L, summary.shares)
        assertTrue(summary.unavailableMetrics.isEmpty())
    }

    /** 조회수는 모든 플랫폼이 보고한다. 합계가 유지돼야 한다. */
    @Test
    @DisplayName("조회수 합계는 플랫폼별로 그대로 합산한다")
    fun viewsAreSummedPerPlatform() {
        given(
            row("YOUTUBE", 1, views = 600),
            row("YOUTUBE", 2, views = 400),
            row("TIKTOK", 3, views = 250),
        )

        val response = useCase.getPlatformComparison(userId, 30)

        assertEquals(1000L, response.platforms.single { it.platform == Platform.YOUTUBE }.views)
        assertEquals(250L, response.platforms.single { it.platform == Platform.TIKTOK }.views)
    }

    // ── 알 수 없는 플랫폼 ────────────────────────────────────────────────────

    /** 알 수 없는 플랫폼을 0 으로 채워 넣으면 다시 같은 종류의 거짓 데이터가 된다. */
    @Test
    @DisplayName("알 수 없는 플랫폼은 건너뛴다")
    fun unknownPlatformIsSkipped() {
        given(row("SOME_NEW_PLATFORM", 1), row("YOUTUBE", 2))

        val response = useCase.getPlatformComparison(userId, 30)

        assertEquals(1, response.platforms.size)
        assertEquals(Platform.YOUTUBE, response.platforms.single().platform)
    }

    @Test
    @DisplayName("행이 없으면 빈 목록이다")
    fun emptyRowsProduceEmptyList() {
        given()

        assertTrue(useCase.getPlatformComparison(userId, 30).platforms.isEmpty())
    }
}
