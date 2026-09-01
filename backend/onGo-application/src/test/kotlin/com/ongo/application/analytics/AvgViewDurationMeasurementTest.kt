package com.ongo.application.analytics

import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.CrossPlatformDetailRaw
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 평균 시청 시간이 **같은 관측에서 나온 분자와 분모로만** 계산되는지 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * val totalWatch = records.sumOf { it.watchTimeSeconds }   // YouTube 만 실측
 * val totalViews = records.sumOf { it.views }              // 전 플랫폼 실측
 * val avg = if (totalViews > 0) totalWatch / totalViews else 0L
 * ```
 *
 * `estimatedMinutesWatched` 를 요청하는 어댑터는 `YouTubeClient` 하나뿐이고
 * (metrics 목록에 있고 `:161` 에서 분 → 초 변환) 나머지 12 개는
 * `watchTimeSeconds = 0` 을 하드코딩한다.
 *
 * - YouTube 가 없는 크리에이터: 분자가 0 이라 **평균이 항상 0초**. `PerformanceView` 는
 *   응답 객체가 항상 오므로 `—` 대신 "0초" 를 그렸다.
 * - YouTube + TikTok 혼합: 분모에 TikTok 조회수가 들어가 **평균이 구조적으로 짧게** 나왔다.
 */
class AvgViewDurationMeasurementTest {

    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val useCase = AnalyticsUseCase(
        analyticsRepository = analyticsRepository,
        userRepository = mockk(relaxed = true),
        videoRepository = mockk(relaxed = true),
        videoUploadRepository = mockk(relaxed = true),
        creditRepository = mockk(relaxed = true),
    )

    private val userId = 7L
    private val today: LocalDate = LocalDate.now()

    /** `findCrossPlatformDetailMetrics` 는 uploadId → 플랫폼 매핑에만 쓴다. */
    private fun uploadOn(platform: String, uploadId: Long) = CrossPlatformDetailRaw(
        videoId = uploadId, videoTitle = "v$uploadId", thumbnailUrls = emptyList(), publishedAt = null,
        platform = platform, videoUploadId = uploadId,
        views = 0, likes = 0, comments = 0, shares = 0,
        watchTimeSeconds = 0, revenueMicro = 0, impressions = 0, avgViewDurationSeconds = 0,
    )

    private fun daily(uploadId: Long, date: LocalDate, views: Int, watchSeconds: Long) = AnalyticsDaily(
        id = uploadId * 1000 + date.dayOfYear,
        videoUploadId = uploadId,
        date = date,
        views = views,
        watchTimeSeconds = watchSeconds,
    )

    private fun given(uploads: List<CrossPlatformDetailRaw>, rows: List<AnalyticsDaily>) {
        every { analyticsRepository.findCrossPlatformDetailMetrics(userId, any()) } returns uploads
        every { analyticsRepository.findAllByUserId(userId) } returns rows
    }

    // ── 측정 불가 ────────────────────────────────────────────────────────────

    /** **이 케이스가 "0초" 를 만들던 자리다.** */
    @Test
    @DisplayName("시청 시간을 보고하지 않는 플랫폼뿐이면 null과 빈 배열이다")
    fun nonReportingPlatformsProduceNullAndEmptyData() {
        given(
            uploads = listOf(uploadOn("TIKTOK", 1), uploadOn("INSTAGRAM", 2)),
            rows = listOf(
                daily(1, today, views = 5000, watchSeconds = 0),
                daily(2, today, views = 3000, watchSeconds = 0),
            ),
        )

        val response = useCase.getAvgViewDuration(userId, 30)

        assertNull(response.avgDurationSeconds, "0초는 '시청이 없었다' 는 관측 결과가 된다")
        assertTrue(response.data.isEmpty(), "측정하지 않은 날짜에 포인트를 만들었다")
        assertTrue(response.unavailableReason!!.isNotBlank())
    }

    /** 조회가 없으면 평균의 분모가 없다. */
    @Test
    @DisplayName("YouTube 행이어도 views가 0이면 제외한다")
    fun youtubeRowWithZeroViewsIsExcluded() {
        given(
            uploads = listOf(uploadOn("YOUTUBE", 1)),
            rows = listOf(daily(1, today, views = 0, watchSeconds = 0)),
        )

        val response = useCase.getAvgViewDuration(userId, 30)

        assertNull(response.avgDurationSeconds)
        assertTrue(response.data.isEmpty())
    }

    @Test
    @DisplayName("빈 기간이면 포인트를 만들지 않는다")
    fun emptyPeriodProducesNoPoints() {
        given(uploads = emptyList(), rows = emptyList())

        val response = useCase.getAvgViewDuration(userId, 30)

        assertNull(response.avgDurationSeconds)
        assertTrue(response.data.isEmpty())
    }

    @Test
    @DisplayName("기간 밖 측정 행은 제외한다")
    fun rowsOutsideTheWindowAreExcluded() {
        given(
            uploads = listOf(uploadOn("YOUTUBE", 1)),
            rows = listOf(daily(1, today.minusDays(60), views = 100, watchSeconds = 12_000)),
        )

        val response = useCase.getAvgViewDuration(userId, 30)

        assertNull(response.avgDurationSeconds)
        assertTrue(response.data.isEmpty())
    }

    /** 알 수 없는 플랫폼 문자열은 통과시키지 않는다 — fail-closed. */
    @Test
    @DisplayName("알 수 없는 플랫폼은 측정 대상에서 제외한다")
    fun unknownPlatformIsFailClosed() {
        given(
            uploads = listOf(uploadOn("SOME_NEW_PLATFORM", 1)),
            rows = listOf(daily(1, today, views = 100, watchSeconds = 12_000)),
        )

        val response = useCase.getAvgViewDuration(userId, 30)

        assertNull(response.avgDurationSeconds, "알 수 없는 플랫폼의 값을 측정값으로 썼다")
        assertTrue(response.data.isEmpty())
    }

    // ── 혼합 플랫폼 왜곡 방지 ────────────────────────────────────────────────

    /**
     * **이 케이스가 평균을 구조적으로 짧게 만들던 자리다.** TikTok 조회수가 분모에
     * 들어가 YouTube 의 실제 평균이 희석됐다.
     */
    @Test
    @DisplayName("YouTube 측정 행만 계산하고 TikTok 조회수를 분모에 섞지 않는다")
    fun mixedPlatformsDoNotContaminateTheDenominator() {
        given(
            uploads = listOf(uploadOn("YOUTUBE", 1), uploadOn("TIKTOK", 2)),
            rows = listOf(
                daily(1, today, views = 100, watchSeconds = 12_000), // 120초
                daily(2, today, views = 99_900, watchSeconds = 0), // TikTok: 시청 시간 미수집
            ),
        )

        val response = useCase.getAvgViewDuration(userId, 30)

        assertEquals(120L, response.avgDurationSeconds, "TikTok 조회수가 분모에 섞였다")
        assertEquals(1, response.data.size, "미측정 플랫폼 포인트가 생겼다")
        assertEquals(100L, response.data.single().totalViews)
    }

    /**
     * 전체 평균은 **조회수로 가중**된다. 날짜별 평균의 산술평균이 아니다 —
     * 조회수가 많은 날이 더 큰 영향을 줘야 실제 평균이다.
     */
    @Test
    @DisplayName("전체 평균은 조회수로 가중한다")
    fun overallAverageIsWeightedByViews() {
        given(
            uploads = listOf(uploadOn("YOUTUBE", 1)),
            rows = listOf(
                daily(1, today.minusDays(1), views = 100, watchSeconds = 6_000), // 60초
                daily(1, today, views = 900, watchSeconds = 108_000), // 120초
            ),
        )

        val response = useCase.getAvgViewDuration(userId, 30)

        // 가중: (6000 + 108000) / (100 + 900) = 114초. 산술평균이면 90초다.
        assertEquals(114L, response.avgDurationSeconds, "조회수 가중이 아니라 산술평균을 썼다")
        assertEquals(listOf(60L, 120L), response.data.map { it.avgDurationSeconds })
    }

    @Test
    @DisplayName("측정 표본의 플랫폼을 응답에 밝힌다")
    fun responseReportsTheMeasuredPlatforms() {
        given(
            uploads = listOf(uploadOn("YOUTUBE", 1)),
            rows = listOf(daily(1, today, views = 100, watchSeconds = 12_000)),
        )

        val response = useCase.getAvgViewDuration(userId, 30)

        assertEquals(listOf("YOUTUBE"), response.measuredPlatforms)
        assertNull(response.unavailableReason)
    }

    /** 실제 표본만 담아야 한다. 이론상 가능한 목록이 아니다. */
    @Test
    @DisplayName("measuredPlatforms 는 합계에 실제로 들어간 플랫폼만 담는다")
    fun measuredPlatformsReflectTheActualSample() {
        given(
            uploads = listOf(uploadOn("TIKTOK", 1)),
            rows = listOf(daily(1, today, views = 1000, watchSeconds = 50_000)),
        )

        val response = useCase.getAvgViewDuration(userId, 30)

        assertTrue(
            response.measuredPlatforms.isEmpty(),
            "표본에 없는 플랫폼이 실렸다: ${response.measuredPlatforms}",
        )
    }

    // ── 측정된 0 은 보존 ─────────────────────────────────────────────────────

    /**
     * **조회가 있는 YouTube 행에서 시청 시간 0 은 측정된 사실이다.**
     *
     * 하드코딩 0 을 넣는 플랫폼은 허용 목록에 없으므로, 이 필터를 통과한 0 은
     * 실제로 관측된 값이다.
     */
    @Test
    @DisplayName("YouTube 유효 행의 시청 시간 0은 관측값으로 보존한다")
    fun measuredZeroWatchTimeIsPreserved() {
        given(
            uploads = listOf(uploadOn("YOUTUBE", 1)),
            rows = listOf(daily(1, today, views = 5000, watchSeconds = 0)),
        )

        val response = useCase.getAvgViewDuration(userId, 30)

        assertEquals(0L, response.avgDurationSeconds, "측정된 0초를 측정 불가로 감췄다")
        assertEquals(0L, response.data.single().avgDurationSeconds)
        assertNull(response.unavailableReason)
    }

    /** 날짜별 포인트는 조회가 측정된 날짜만, 그 날짜의 행끼리만 계산한다. */
    @Test
    @DisplayName("측정된 날짜만 포인트를 만들고 날짜별로 따로 계산한다")
    fun pointsExistOnlyForMeasuredDates() {
        given(
            uploads = listOf(uploadOn("YOUTUBE", 1)),
            rows = listOf(
                daily(1, today.minusDays(2), views = 100, watchSeconds = 3_000), // 30초
                daily(1, today.minusDays(1), views = 0, watchSeconds = 0), // 미측정 → 포인트 없음
                daily(1, today, views = 100, watchSeconds = 9_000), // 90초
            ),
        )

        val response = useCase.getAvgViewDuration(userId, 30)

        assertEquals(2, response.data.size, "미측정 날짜에 0 포인트를 만들었다")
        assertEquals(listOf(30L, 90L), response.data.map { it.avgDurationSeconds })
        assertEquals(60L, response.avgDurationSeconds)
    }
}
