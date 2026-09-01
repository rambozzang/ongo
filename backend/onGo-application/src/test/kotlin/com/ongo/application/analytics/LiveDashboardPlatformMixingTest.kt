package com.ongo.application.analytics

import com.ongo.application.analytics.dto.LiveMetricResponse
import com.ongo.common.enums.Platform
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.LiveAlertConfigRepository
import com.ongo.domain.analytics.LiveAlertRepository
import com.ongo.domain.analytics.RevenueStatus
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.revenue.PlatformRevenueStatusCount
import com.ongo.domain.revenue.RevenueRepository
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
 * 라이브 대시보드 지표값이 **플랫폼별 측정 행으로만** 만들어지는지 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * `AnalyticsJooqRepository.getDailyAggregates` 는 `analytics_daily` 를 **날짜로만** 묶는다
 * (`video_uploads` 와 조인하지 않는다). 그래서 카드의 숫자 자체가 오염됐다.
 *
 * - `TumblrClient.kt:141` 의 `total_notes`(좋아요+리블로그+답글 총합) → **VIEWS**
 * - `PinterestClient.kt:158/160` 의 `SAVE`(저장)·`PIN_CLICK`(클릭) → **LIKES**
 *
 * 이전 수정은 "이 지표를 주는 플랫폼이 하나라도 있는가" 만 판정했다. 그건 카드를
 * 통째로 닫을지 말지의 문제였고, **열린 카드 안의 숫자가 섞이는 것**은 그대로 남아
 * 있었다. YouTube 와 Tumblr 를 같이 쓰는 크리에이터의 조회수 카드가 대표적이다.
 *
 * ## 여기서 고정하는 것
 *
 * - unsupported 만 있으면 → `null` + 빈 history
 * - 혼합이면 → **지원 행만** 합산
 * - 실측 0 은 → `0`
 */
class LiveDashboardPlatformMixingTest {

    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val liveAlertRepository = mockk<LiveAlertRepository>()
    private val liveAlertConfigRepository = mockk<LiveAlertConfigRepository>()
    private val channelRepository = mockk<ChannelRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>()
    private val revenueRepository = mockk<RevenueRepository>()

    private val useCase = LiveDashboardUseCase(
        analyticsRepository = analyticsRepository,
        liveAlertRepository = liveAlertRepository,
        liveAlertConfigRepository = liveAlertConfigRepository,
        channelRepository = channelRepository,
        videoUploadRepository = videoUploadRepository,
        revenueRepository = revenueRepository,
    )

    private val userId = 7L
    private val today: LocalDate = LocalDate.now()

    private fun row(
        uploadId: Long,
        date: LocalDate = today,
        views: Int = 0,
        likes: Int = 0,
        comments: Int = 0,
        watchTime: Long = 0,
        revenue: Long = 0,
        revenueStatus: RevenueStatus = RevenueStatus.UNSUPPORTED,
    ) = AnalyticsDaily(
        videoUploadId = uploadId,
        date = date,
        views = views,
        likes = likes,
        commentsCount = comments,
        watchTimeSeconds = watchTime,
        revenueMicro = revenue,
        revenueStatus = revenueStatus,
    )

    private fun metrics(
        platforms: Map<Long, Platform>,
        rows: List<AnalyticsDaily>,
        revenueMeasured: Boolean = true,
    ): List<LiveMetricResponse> {
        every { videoUploadRepository.findByUserId(userId) } returns platforms.map { (id, platform) ->
            VideoUpload(id = id, videoId = id, platform = platform, channelId = 1L)
        }
        every { analyticsRepository.findByVideoUploadIdsAndDateRange(any(), any(), any()) } returns
            rows.groupBy { it.videoUploadId }
        every { revenueRepository.getRevenueStatusCounts(userId, any(), any()) } returns
            if (revenueMeasured) {
                listOf(PlatformRevenueStatusCount("YOUTUBE", RevenueStatus.MEASURED.name, 1L))
            } else {
                emptyList()
            }
        every { liveAlertRepository.findByUserId(userId) } returns emptyList()
        every { channelRepository.findByUserId(userId) } returns emptyList()

        return useCase.getLiveState(userId).metrics
    }

    private fun List<LiveMetricResponse>.of(type: String) = single { it.type == type }

    // ══ unsupported 만 있을 때 ══════════════════════════════════════════════

    /** **이 케이스가 노트 총합 90 만을 "조회수" 로 그리던 자리다.** */
    @Test
    @DisplayName("Tumblr만 쓰면 조회수는 null이고 history가 비어 있다")
    fun tumblrOnlyProducesNullViews() {
        val views = metrics(
            platforms = mapOf(1L to Platform.TUMBLR),
            rows = listOf(row(1L, views = 900_000)),
        ).of("VIEWS")

        assertNull(views.currentValue, "노트 총합을 조회수로 내보냈다")
        assertNull(views.previousValue)
        assertEquals(LiveDashboardUseCase.TREND_UNKNOWN, views.trend)
        assertTrue(views.history.isEmpty(), "재지 않은 계열을 그렸다")
        assertEquals(LiveDashboardUseCase.METRIC_NOT_COLLECTED, views.unavailableReason)
    }

    /** Pinterest 의 `SAVE` 는 좋아요가 아니다. */
    @Test
    @DisplayName("Pinterest만 쓰면 좋아요는 null이다")
    fun pinterestOnlyProducesNullLikes() {
        val likes = metrics(
            platforms = mapOf(1L to Platform.PINTEREST),
            rows = listOf(row(1L, likes = 5_000)),
        ).of("LIKES")

        assertNull(likes.currentValue, "저장 수를 좋아요로 내보냈다")
        assertTrue(likes.history.isEmpty())
    }

    // ══ 혼합 플랫폼 ═════════════════════════════════════════════════════════

    /**
     * **여기가 이번 수정의 핵심이다.** 카드는 열려야 한다(YouTube 가 조회수를 준다).
     * 하지만 그 안의 숫자에 Tumblr 행이 섞이면 안 된다.
     */
    @Test
    @DisplayName("혼합 채널의 조회수는 지원하는 플랫폼 행만 합산한다")
    fun mixedPlatformsSumOnlySupportedRows() {
        val views = metrics(
            platforms = mapOf(1L to Platform.YOUTUBE, 2L to Platform.TUMBLR),
            rows = listOf(
                row(1L, views = 400),      // YouTube — 실제 조회수
                row(2L, views = 900_000),  // Tumblr — 노트 총합
            ),
        ).of("VIEWS")

        assertEquals(400L, views.currentValue, "Tumblr 노트 총합이 조회수에 섞였다")
        assertNull(views.unavailableReason, "YouTube 가 주는 지표인데 카드를 닫았다")
    }

    /** 같은 날짜에 여러 지원 행이 있으면 그 행들끼리는 정상적으로 합산된다. */
    @Test
    @DisplayName("지원하는 플랫폼끼리는 정상적으로 합산한다")
    fun supportedPlatformsAreSummedTogether() {
        val views = metrics(
            platforms = mapOf(1L to Platform.YOUTUBE, 2L to Platform.TIKTOK, 3L to Platform.TUMBLR),
            rows = listOf(row(1L, views = 300), row(2L, views = 200), row(3L, views = 900_000)),
        ).of("VIEWS")

        assertEquals(500L, views.currentValue)
    }

    /** history 도 같은 규칙을 따라야 한다 — 그래프가 섞이면 카드보다 더 오래 남는다. */
    @Test
    @DisplayName("history도 지원 행만으로 만든다")
    fun historyUsesOnlySupportedRows() {
        val views = metrics(
            platforms = mapOf(1L to Platform.YOUTUBE, 2L to Platform.TUMBLR),
            rows = listOf(
                row(1L, date = today.minusDays(1), views = 100),
                row(1L, date = today, views = 150),
                row(2L, date = today, views = 900_000),
            ),
        ).of("VIEWS")

        assertEquals(listOf(100L, 150L), views.history.map { it.value }, "history 에 노트 총합이 섞였다")
        assertEquals(150L, views.currentValue)
        assertEquals(100L, views.previousValue)
        assertEquals(50.0, views.changePercent)
        assertEquals("UP", views.trend)
    }

    /** 지원 플랫폼에 게시했지만 아직 수집 전이면 미측정이다 — 0 이 아니다. */
    @Test
    @DisplayName("지원 플랫폼 행이 아직 없으면 0이 아니라 null이다")
    fun supportedPlatformWithoutRowsIsNotZero() {
        val watchTime = metrics(
            platforms = mapOf(1L to Platform.YOUTUBE, 2L to Platform.TUMBLR),
            // Tumblr 행만 수집됐다. YouTube 는 시청 시간을 주지만 아직 동기화 전이다.
            rows = listOf(row(2L, views = 900_000)),
        ).of("WATCH_TIME")

        assertNull(watchTime.currentValue, "수집 전 상태를 0 으로 위장했다")
        assertEquals(LiveDashboardUseCase.METRIC_NOT_MEASURED_YET, watchTime.unavailableReason)
        assertTrue(watchTime.history.isEmpty())
    }

    // ══ 실측 0 은 보존 ══════════════════════════════════════════════════════

    @Test
    @DisplayName("지원 플랫폼의 측정된 0은 0으로 남는다")
    fun measuredZeroOnSupportedPlatformStaysZero() {
        val views = metrics(
            platforms = mapOf(1L to Platform.YOUTUBE, 2L to Platform.TUMBLR),
            rows = listOf(row(1L, views = 0), row(2L, views = 900_000)),
        ).of("VIEWS")

        assertEquals(0L, views.currentValue, "실측 0 을 미측정으로 감췄다")
        assertNull(views.unavailableReason)
        assertEquals(1, views.history.size)
    }

    // ══ 수익: 행 단위 상태 ══════════════════════════════════════════════════

    /**
     * `AnalyticsDaily.revenueMicro` 는 `revenueStatus == MEASURED` 일 때만 의미가 있다
     * (도메인 주석). 미지원 행의 숫자를 더하면 없는 수익이 생긴다.
     */
    @Test
    @DisplayName("수익은 실측 상태인 행만 합산한다")
    fun revenueSumsOnlyMeasuredRows() {
        val revenue = metrics(
            platforms = mapOf(1L to Platform.YOUTUBE),
            rows = listOf(
                row(1L, revenue = 5_000, revenueStatus = RevenueStatus.MEASURED),
                row(1L, date = today.minusDays(2), revenue = 999_999, revenueStatus = RevenueStatus.UNSUPPORTED),
            ),
        ).of("REVENUE")

        assertEquals(5_000L, revenue.currentValue, "미지원 상태 행의 숫자가 수익에 섞였다")
        assertEquals(1, revenue.history.size)
    }

    /** 기존 `revenue_status` 판정은 그대로다 — 열지 못하면 값도 없다. */
    @Test
    @DisplayName("수익 상태 판정이 닫히면 값도 내지 않는다")
    fun revenueStatusJudgmentStillCloses() {
        val revenue = metrics(
            platforms = mapOf(1L to Platform.YOUTUBE),
            rows = listOf(row(1L, revenue = 5_000, revenueStatus = RevenueStatus.MEASURED)),
            revenueMeasured = false,
        ).of("REVENUE")

        assertNull(revenue.currentValue)
        assertTrue(revenue.history.isEmpty())
        assertTrue(!revenue.unavailableReason.isNullOrBlank(), "닫힌 이유를 알리지 않았다")
    }

    /** 여섯 카드는 그대로 나온다 — 응답 계약을 바꾸지 않았다. */
    @Test
    @DisplayName("지표 카드 여섯 개 계약을 유지한다")
    fun sixMetricCardsRemain() {
        val all = metrics(mapOf(1L to Platform.TUMBLR), listOf(row(1L, views = 10)))

        assertEquals(
            listOf("VIEWS", "SUBSCRIBERS", "LIKES", "COMMENTS", "WATCH_TIME", "REVENUE"),
            all.map { it.type },
        )
    }
}
