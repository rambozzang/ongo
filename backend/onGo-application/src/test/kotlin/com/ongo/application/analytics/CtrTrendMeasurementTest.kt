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
 * CTR 추세가 **같은 관측에서 나온 분자와 분모로만** 계산되는지 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * val allAnalytics = analyticsRepository.findAllByUserId(userId)   // 전 플랫폼
 * val totalImpressions = records.sumOf { it.impressions }          // YouTube 만 실측
 * val totalViews = records.sumOf { it.views }                      // 전 플랫폼 실측
 * val ctr = if (totalImpressions > 0) ... else 0.0
 * ```
 *
 * `impressions` 를 요청하는 어댑터는 `YouTubeClient` 하나뿐이고(metrics 목록에 `impressions`
 * 가 있다) 나머지 12 개는 그 값을 세팅하지 않아 0 으로 남는다. 결과는 둘 중 하나였다.
 *
 * - YouTube 가 없는 크리에이터: 분모가 0 이라 **CTR 이 항상 정확히 0.0%**.
 *   화면은 "평균 CTR 0% · 총 노출 0" 을 성과처럼 보여줬다 — 재지 않았을 뿐이다.
 * - YouTube + TikTok 혼합: 분자에 TikTok 조회수가 들어가고 분모는 YouTube 노출뿐이라
 *   **CTR 이 100% 를 넘었다.**
 */
class CtrTrendMeasurementTest {

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

    private fun daily(uploadId: Long, date: LocalDate, views: Int, impressions: Int) = AnalyticsDaily(
        id = uploadId * 1000 + date.dayOfYear,
        videoUploadId = uploadId,
        date = date,
        views = views,
        impressions = impressions,
    )

    private fun given(uploads: List<CrossPlatformDetailRaw>, rows: List<AnalyticsDaily>) {
        every { analyticsRepository.findCrossPlatformDetailMetrics(userId, any()) } returns uploads
        every { analyticsRepository.findAllByUserId(userId) } returns rows
    }

    // ── 측정 불가 ────────────────────────────────────────────────────────────

    /** **이 케이스가 "평균 CTR 0% · 총 노출 0" 을 만들던 자리다.** */
    @Test
    @DisplayName("노출을 보고하지 않는 플랫폼뿐이면 null과 빈 배열이다")
    fun nonReportingPlatformsProduceNullAndEmptyData() {
        given(
            uploads = listOf(uploadOn("TIKTOK", 1), uploadOn("INSTAGRAM", 2)),
            rows = listOf(
                daily(1, today, views = 5000, impressions = 0),
                daily(2, today, views = 3000, impressions = 0),
            ),
        )

        val response = useCase.getCTRTrend(userId, 30)

        assertNull(response.avgCTR, "0.0% 는 '클릭이 없었다' 는 관측 결과가 된다")
        assertNull(response.totalImpressions, "0 은 '노출이 0회였다' 는 주장이 된다")
        assertTrue(response.data.isEmpty(), "측정하지 않은 날짜에 포인트를 만들었다")
        assertTrue(response.unavailableReason!!.isNotBlank())
    }

    /**
     * 플랫폼이 노출을 보고하더라도 **그날 값이 0 이면 분모가 없다.** 플랫폼만 보고
     * 통과시키면 YouTube 의 미수집 0 이 다시 분모 자리에 들어간다.
     */
    @Test
    @DisplayName("YouTube 행이어도 impressions가 0이면 제외한다")
    fun youtubeRowWithZeroImpressionsIsExcluded() {
        given(
            uploads = listOf(uploadOn("YOUTUBE", 1)),
            rows = listOf(daily(1, today, views = 900, impressions = 0)),
        )

        val response = useCase.getCTRTrend(userId, 30)

        assertNull(response.avgCTR)
        assertNull(response.totalImpressions)
        assertTrue(response.data.isEmpty())
    }

    @Test
    @DisplayName("빈 기간이면 포인트를 만들지 않는다")
    fun emptyPeriodProducesNoPoints() {
        given(uploads = emptyList(), rows = emptyList())

        val response = useCase.getCTRTrend(userId, 30)

        assertNull(response.avgCTR)
        assertNull(response.totalImpressions)
        assertTrue(response.data.isEmpty())
    }

    /** 기간 밖 행은 세지 않는다. */
    @Test
    @DisplayName("기간 밖 측정 행은 제외한다")
    fun rowsOutsideTheWindowAreExcluded() {
        given(
            uploads = listOf(uploadOn("YOUTUBE", 1)),
            rows = listOf(daily(1, today.minusDays(60), views = 100, impressions = 1000)),
        )

        val response = useCase.getCTRTrend(userId, 30)

        assertNull(response.avgCTR)
        assertTrue(response.data.isEmpty())
    }

    /** 알 수 없는 플랫폼 문자열은 통과시키지 않는다 — fail-closed. */
    @Test
    @DisplayName("알 수 없는 플랫폼은 측정 대상에서 제외한다")
    fun unknownPlatformIsFailClosed() {
        given(
            uploads = listOf(uploadOn("SOME_NEW_PLATFORM", 1)),
            rows = listOf(daily(1, today, views = 100, impressions = 1000)),
        )

        val response = useCase.getCTRTrend(userId, 30)

        assertNull(response.avgCTR, "알 수 없는 플랫폼의 값을 측정값으로 썼다")
        assertTrue(response.data.isEmpty())
    }

    // ── 혼합 플랫폼 왜곡 방지 ────────────────────────────────────────────────

    /**
     * **이 케이스가 CTR 100% 초과를 만들던 자리다.** TikTok 조회수가 분자에 들어가고
     * 분모는 YouTube 노출뿐이었다.
     */
    @Test
    @DisplayName("YouTube 측정 행만 계산하고 다른 플랫폼 조회수를 섞지 않는다")
    fun mixedPlatformsDoNotContaminateTheNumerator() {
        given(
            uploads = listOf(uploadOn("YOUTUBE", 1), uploadOn("TIKTOK", 2)),
            rows = listOf(
                daily(1, today, views = 250, impressions = 1000), // YouTube: 25%
                daily(2, today, views = 90_000, impressions = 0), // TikTok: 노출 미수집
            ),
        )

        val response = useCase.getCTRTrend(userId, 30)

        assertEquals(25.0, response.avgCTR, "TikTok 조회수가 분자에 섞였다")
        assertEquals(1000L, response.totalImpressions)
        assertEquals(1, response.data.size, "미측정 플랫폼 포인트가 생겼다")
        assertEquals(250L, response.data.single().views, "포인트의 views 가 같은 행에서 오지 않았다")
        assertTrue(response.avgCTR!! <= 100.0, "CTR 이 100% 를 넘었다")
    }

    /** 표본이 어느 플랫폼인지 응답이 밝혀야 한다. 합계만 보면 모집단을 알 수 없다. */
    @Test
    @DisplayName("측정 표본의 플랫폼을 응답에 밝힌다")
    fun responseReportsTheMeasuredPlatforms() {
        given(
            uploads = listOf(uploadOn("YOUTUBE", 1)),
            rows = listOf(daily(1, today, views = 250, impressions = 1000)),
        )

        val response = useCase.getCTRTrend(userId, 30)

        assertEquals(listOf("YOUTUBE"), response.measuredPlatforms)
        assertNull(response.unavailableReason)
    }

    // ── 측정된 값은 보존 ─────────────────────────────────────────────────────

    /** **`impressions > 0` 인데 조회가 0 이면 그 0% 는 측정된 사실이다.** */
    @Test
    @DisplayName("노출이 있고 조회가 0이면 CTR 0을 보존한다")
    fun measuredZeroCtrIsPreserved() {
        given(
            uploads = listOf(uploadOn("YOUTUBE", 1)),
            rows = listOf(daily(1, today, views = 0, impressions = 5000)),
        )

        val response = useCase.getCTRTrend(userId, 30)

        assertEquals(0.0, response.avgCTR, "측정된 0% 를 측정 불가로 감췄다")
        assertEquals(5000L, response.totalImpressions)
        assertEquals(0.0, response.data.single().ctr)
    }

    /** 날짜별 포인트는 측정된 날짜만, 그 날짜의 행끼리만 계산한다. */
    @Test
    @DisplayName("측정된 날짜만 포인트를 만들고 날짜별로 따로 계산한다")
    fun pointsExistOnlyForMeasuredDates() {
        given(
            uploads = listOf(uploadOn("YOUTUBE", 1)),
            rows = listOf(
                daily(1, today.minusDays(2), views = 100, impressions = 1000), // 10%
                daily(1, today.minusDays(1), views = 0, impressions = 0), // 미측정 → 포인트 없음
                daily(1, today, views = 60, impressions = 200), // 30%
            ),
        )

        val response = useCase.getCTRTrend(userId, 30)

        assertEquals(2, response.data.size, "미측정 날짜에 0 포인트를 만들었다")
        assertEquals(listOf(10.0, 30.0), response.data.map { it.ctr })
        // 전체 평균은 합계 기준이다: 160 / 1200 = 13.33%
        assertEquals(13.33, response.avgCTR)
        assertEquals(1200L, response.totalImpressions)
    }
    /**
     * **`measuredPlatforms` 는 실제 표본이어야 한다.**
     *
     * 예전에는 `platformsReporting(...)` — 그 지표를 보고할 수 있는 플랫폼 **전체** —
     * 를 내보냈다. 그건 "이 크리에이터의 표본" 이 아니라 "이론상 가능한 목록" 이다.
     * 합계에 들어가지 않은 플랫폼이 표본으로 실리면 모집단을 오해한다.
     */
    @Test
    @DisplayName("measuredPlatforms 는 합계에 실제로 들어간 플랫폼만 담는다")
    fun measuredPlatformsReflectTheActualSample() {
        given(
            uploads = listOf(uploadOn("TIKTOK", 1)),
            rows = listOf(daily(1, today, views = 1000, impressions = 50)),
        )

        val response = useCase.getCTRTrend(userId, 30)

        // TikTok 은 이 지표를 보고하지 않으므로 표본이 비어 있다.
        assertTrue(
            response.measuredPlatforms.isEmpty(),
            "표본에 없는 플랫폼이 실렸다: ${response.measuredPlatforms}",
        )
    }
}
