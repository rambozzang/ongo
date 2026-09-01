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
 * 구독 전환이 **같은 관측에서 나온 분자와 분모로만** 계산되는지 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * val gained = records.sumOf { it.subscriberGained }   // YouTube 만 실측
 * val views = records.sumOf { it.views }               // 전 플랫폼 실측
 * val rate = if (views > 0) ... else 0.0
 * ```
 *
 * `subscribersGained` 를 요청하는 어댑터는 `YouTubeClient` 하나뿐이고(metrics 목록에 있고
 * `:162` 에서 파싱) 나머지 12 개는 `subscriberGained = 0` 을 하드코딩한다.
 *
 * - YouTube 가 없는 크리에이터: 분자가 0 이라 **전환율이 항상 0%**, `totalGained` 도 0.
 *   `SubscriberConversionChart.vue` 는 `총 신규 구독: **+0**` 을 초록색으로 보여줬다.
 * - YouTube + TikTok 혼합: 분모에 TikTok 조회수가 들어가 **전환율이 구조적으로 낮게** 나왔다.
 */
class SubscriberConversionMeasurementTest {

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

    private fun daily(uploadId: Long, date: LocalDate, views: Int, gained: Int) = AnalyticsDaily(
        id = uploadId * 1000 + date.dayOfYear,
        videoUploadId = uploadId,
        date = date,
        views = views,
        subscriberGained = gained,
    )

    private fun given(uploads: List<CrossPlatformDetailRaw>, rows: List<AnalyticsDaily>) {
        every { analyticsRepository.findCrossPlatformDetailMetrics(userId, any()) } returns uploads
        every { analyticsRepository.findAllByUserId(userId) } returns rows
    }

    // ── 측정 불가 ────────────────────────────────────────────────────────────

    /** **이 케이스가 초록색 "+0" 을 만들던 자리다.** */
    @Test
    @DisplayName("구독 증가를 보고하지 않는 플랫폼뿐이면 null과 빈 배열이다")
    fun nonReportingPlatformsProduceNullAndEmptyData() {
        given(
            uploads = listOf(uploadOn("TIKTOK", 1), uploadOn("INSTAGRAM", 2)),
            rows = listOf(
                daily(1, today, views = 5000, gained = 0),
                daily(2, today, views = 3000, gained = 0),
            ),
        )

        val response = useCase.getSubscriberConversion(userId, 30)

        assertNull(response.totalGained, "0 은 '신규 구독 0명' 이라는 성과 주장이 된다")
        assertTrue(response.data.isEmpty(), "측정하지 않은 날짜에 포인트를 만들었다")
        assertTrue(response.unavailableReason!!.isNotBlank())
    }

    /** 조회가 없으면 전환율의 분모가 없다. */
    @Test
    @DisplayName("YouTube 행이어도 views가 0이면 제외한다")
    fun youtubeRowWithZeroViewsIsExcluded() {
        given(
            uploads = listOf(uploadOn("YOUTUBE", 1)),
            rows = listOf(daily(1, today, views = 0, gained = 0)),
        )

        val response = useCase.getSubscriberConversion(userId, 30)

        assertNull(response.totalGained)
        assertTrue(response.data.isEmpty())
    }

    @Test
    @DisplayName("빈 기간이면 포인트를 만들지 않는다")
    fun emptyPeriodProducesNoPoints() {
        given(uploads = emptyList(), rows = emptyList())

        val response = useCase.getSubscriberConversion(userId, 30)

        assertNull(response.totalGained)
        assertTrue(response.data.isEmpty())
    }

    @Test
    @DisplayName("기간 밖 측정 행은 제외한다")
    fun rowsOutsideTheWindowAreExcluded() {
        given(
            uploads = listOf(uploadOn("YOUTUBE", 1)),
            rows = listOf(daily(1, today.minusDays(60), views = 1000, gained = 50)),
        )

        val response = useCase.getSubscriberConversion(userId, 30)

        assertNull(response.totalGained)
        assertTrue(response.data.isEmpty())
    }

    /** 알 수 없는 플랫폼 문자열은 통과시키지 않는다 — fail-closed. */
    @Test
    @DisplayName("알 수 없는 플랫폼은 측정 대상에서 제외한다")
    fun unknownPlatformIsFailClosed() {
        given(
            uploads = listOf(uploadOn("SOME_NEW_PLATFORM", 1)),
            rows = listOf(daily(1, today, views = 1000, gained = 50)),
        )

        val response = useCase.getSubscriberConversion(userId, 30)

        assertNull(response.totalGained, "알 수 없는 플랫폼의 값을 측정값으로 썼다")
        assertTrue(response.data.isEmpty())
    }

    // ── 혼합 플랫폼 왜곡 방지 ────────────────────────────────────────────────

    /**
     * **이 케이스가 전환율을 구조적으로 낮게 만들던 자리다.** TikTok 조회수가 분모에
     * 들어가 YouTube 의 실제 전환율이 희석됐다.
     */
    @Test
    @DisplayName("YouTube 측정 행만 계산하고 TikTok 조회수를 분모에 섞지 않는다")
    fun mixedPlatformsDoNotContaminateTheDenominator() {
        given(
            uploads = listOf(uploadOn("YOUTUBE", 1), uploadOn("TIKTOK", 2)),
            rows = listOf(
                daily(1, today, views = 1000, gained = 50), // YouTube: 5%
                daily(2, today, views = 99_000, gained = 0), // TikTok: 구독 증가 미수집
            ),
        )

        val response = useCase.getSubscriberConversion(userId, 30)

        assertEquals(50L, response.totalGained)
        assertEquals(1, response.data.size, "미측정 플랫폼 포인트가 생겼다")
        val point = response.data.single()
        assertEquals(1000L, point.views, "TikTok 조회수가 분모에 섞였다")
        assertEquals(5.0, point.conversionRate)
    }

    @Test
    @DisplayName("측정 표본의 플랫폼을 응답에 밝힌다")
    fun responseReportsTheMeasuredPlatforms() {
        given(
            uploads = listOf(uploadOn("YOUTUBE", 1)),
            rows = listOf(daily(1, today, views = 1000, gained = 50)),
        )

        val response = useCase.getSubscriberConversion(userId, 30)

        assertEquals(listOf("YOUTUBE"), response.measuredPlatforms)
        assertNull(response.unavailableReason)
    }

    // ── 측정된 0 은 보존 ─────────────────────────────────────────────────────

    /** **조회가 있는 YouTube 행에서 구독 증가 0 은 측정된 사실이다.** */
    @Test
    @DisplayName("YouTube 유효 행의 구독 증가 0은 관측값으로 보존한다")
    fun measuredZeroGainedIsPreserved() {
        given(
            uploads = listOf(uploadOn("YOUTUBE", 1)),
            rows = listOf(daily(1, today, views = 5000, gained = 0)),
        )

        val response = useCase.getSubscriberConversion(userId, 30)

        assertEquals(0L, response.totalGained, "측정된 0 을 측정 불가로 감췄다")
        assertEquals(0.0, response.data.single().conversionRate)
        assertNull(response.unavailableReason)
    }

    /** 날짜별 포인트는 조회가 측정된 날짜만, 그 날짜의 행끼리만 계산한다. */
    @Test
    @DisplayName("측정된 날짜만 포인트를 만들고 날짜별로 따로 계산한다")
    fun pointsExistOnlyForMeasuredDates() {
        given(
            uploads = listOf(uploadOn("YOUTUBE", 1)),
            rows = listOf(
                daily(1, today.minusDays(2), views = 1000, gained = 20), // 2%
                daily(1, today.minusDays(1), views = 0, gained = 0), // 미측정 → 포인트 없음
                daily(1, today, views = 500, gained = 25), // 5%
            ),
        )

        val response = useCase.getSubscriberConversion(userId, 30)

        assertEquals(2, response.data.size, "미측정 날짜에 0 포인트를 만들었다")
        assertEquals(listOf(2.0, 5.0), response.data.map { it.conversionRate })
        assertEquals(45L, response.totalGained)
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
            rows = listOf(daily(1, today, views = 1000, gained = 50)),
        )

        val response = useCase.getSubscriberConversion(userId, 30)

        // TikTok 은 이 지표를 보고하지 않으므로 표본이 비어 있다.
        assertTrue(
            response.measuredPlatforms.isEmpty(),
            "표본에 없는 플랫폼이 실렸다: ${response.measuredPlatforms}",
        )
    }
}
