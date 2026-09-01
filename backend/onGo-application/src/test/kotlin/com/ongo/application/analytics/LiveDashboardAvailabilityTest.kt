package com.ongo.application.analytics

import com.ongo.common.enums.Platform
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.LiveAlert
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 라이브 대시보드 여섯 카드가 **물어볼 곳 없는 지표를 0 으로 그리지 않는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * 여섯 지표 중 `SUBSCRIBERS`·`WATCH_TIME`·`REVENUE` 는 `YouTubeClient` 만 조회한다
 * ([PlatformMetricAvailability]). TikTok·Instagram 만 쓰는 크리에이터에게 그 세 카드의
 * 합계 0 은 **"오늘 0" 이 아니라 물어볼 곳이 없다**는 뜻인데, 실제로 0 건이었던
 * 사용자와 완전히 같은 모양으로 나갔다.
 *
 * ## 여기서 고정하는 것은 "카드를 여는가" 뿐이다
 *
 * 수집하지 않는 플랫폼이 **하드코딩 0** 을 저장하는 한, 합계는 그 0 이 더해져도 바뀌지
 * 않는다. 그래서 이 파일은 "물어볼 곳이 있었는가" 만 판정한다.
 *
 * 하지만 그것만으로는 부족하다. Tumblr·Pinterest 처럼 **다른 뜻의 큰 숫자**를 같은
 * 컬럼에 넣는 어댑터가 있어서, 열린 카드 안의 합계 자체가 오염된다. 그 행 단위 계약은
 * [LiveDashboardPlatformMixingTest] 가 따로 고정한다.
 */
class LiveDashboardAvailabilityTest {

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

    private fun given(
        platforms: List<Platform>,
        revenueCounts: List<PlatformRevenueStatusCount> = emptyList(),
        alerts: List<LiveAlert> = emptyList(),
    ) {
        val today = LocalDate.now()
        // 측정 행은 **첫 번째 플랫폼**(id=100)이 만든 것으로 둔다. 유스케이스가 행마다
        // 플랫폼을 확인하므로 날짜별 합계가 아니라 원시 행을 준다.
        val rows = listOf(
            row(today.minusDays(1), views = 100, likes = 10, comments = 5, watchTime = 1_000, subs = 3, revenue = 5_000_000),
            row(today, views = 200, likes = 20, comments = 8, watchTime = 2_000, subs = 6, revenue = 9_000_000),
        )
        every { analyticsRepository.findByVideoUploadIdsAndDateRange(any(), any(), any()) } returns
            rows.groupBy { it.videoUploadId }
        every { videoUploadRepository.findByUserId(userId) } returns platforms.mapIndexed { i, p ->
            VideoUpload(id = 100L + i, videoId = 1L, platform = p, channelId = 1L)
        }
        every { revenueRepository.getRevenueStatusCounts(userId, any(), any()) } returns revenueCounts
        every { liveAlertRepository.findByUserId(userId) } returns alerts
        every { channelRepository.findByUserId(userId) } returns emptyList()
    }

    /** 수익은 행 단위 상태까지 봐야 하므로 실측으로 둔다. */
    private fun row(
        date: LocalDate,
        views: Int,
        likes: Int,
        comments: Int,
        watchTime: Long,
        subs: Int,
        revenue: Long,
    ) = AnalyticsDaily(
        videoUploadId = 100L,
        date = date,
        views = views,
        likes = likes,
        commentsCount = comments,
        watchTimeSeconds = watchTime,
        subscriberGained = subs,
        revenueMicro = revenue,
        revenueStatus = RevenueStatus.MEASURED,
    )

    private fun metric(type: String) = useCase.getLiveState(userId).metrics.single { it.type == type }

    // ── YouTube 전용 지표 ────────────────────────────────────────────────────

    /** **이 케이스가 "신규 구독 0명" 을 성과로 그리던 자리다.** */
    @Test
    @DisplayName("구독 증가를 수집하는 플랫폼이 없으면 값을 만들지 않는다")
    fun subscribersAreUnavailableWithoutYouTube() {
        given(listOf(Platform.TIKTOK, Platform.INSTAGRAM))

        val subscribers = metric("SUBSCRIBERS")

        assertNull(subscribers.currentValue, "물어볼 곳이 없는데 0 을 성과로 내보냈다")
        assertNull(subscribers.previousValue)
        assertEquals(LiveDashboardUseCase.METRIC_NOT_COLLECTED, subscribers.unavailableReason)
        assertEquals(LiveDashboardUseCase.TREND_UNKNOWN, subscribers.trend)
        // 0 선을 그으면 "계속 0 이었다" 로 보인다.
        assertTrue(subscribers.history.isEmpty())
    }

    @Test
    @DisplayName("시청 시간을 수집하는 플랫폼이 없으면 값을 만들지 않는다")
    fun watchTimeIsUnavailableWithoutYouTube() {
        given(listOf(Platform.TIKTOK))

        assertNull(metric("WATCH_TIME").currentValue, "시청 시간 0 을 성과로 내보냈다")
    }

    /** 조회수·좋아요·댓글은 TikTok 도 준다 — 함께 죽으면 안 된다. */
    @Test
    @DisplayName("수집 가능한 지표는 그대로 값을 낸다")
    fun collectableMetricsKeepTheirValues() {
        given(listOf(Platform.TIKTOK))

        assertEquals(200L, metric("VIEWS").currentValue)
        assertEquals(20L, metric("LIKES").currentValue)
        assertEquals(8L, metric("COMMENTS").currentValue)
    }

    @Test
    @DisplayName("YouTube 가 있으면 여섯 지표를 모두 낸다")
    fun youtubeMakesEveryMetricCollectable() {
        given(
            listOf(Platform.YOUTUBE),
            revenueCounts = listOf(PlatformRevenueStatusCount("YOUTUBE", RevenueStatus.MEASURED.name, 2L)),
        )

        listOf("VIEWS", "SUBSCRIBERS", "LIKES", "COMMENTS", "WATCH_TIME", "REVENUE").forEach { type ->
            assertNotNull(metric(type).currentValue, "$type 을 비웠다")
            assertNull(metric(type).unavailableReason)
        }
    }

    // ── 수익은 플랫폼만으로 부족하다 ─────────────────────────────────────────

    /**
     * YouTube 를 연결했어도 **재연동 전이면 수익을 못 읽는다.** 그때의 0 은 "수익 0 원" 이
     * 아니다. 이미 있는 [com.ongo.application.revenue.RevenueAvailability] 판정을 쓴다.
     */
    @Test
    @DisplayName("수익 권한이 없으면 YouTube 가 있어도 수익을 만들지 않는다")
    fun revenueNeedsMeasuredStatusNotJustPlatform() {
        given(
            listOf(Platform.YOUTUBE),
            revenueCounts = listOf(
                PlatformRevenueStatusCount("YOUTUBE", RevenueStatus.PERMISSION_REQUIRED.name, 2L),
            ),
        )

        val revenue = metric("REVENUE")

        assertNull(revenue.currentValue, "권한이 없는데 수익을 성과로 내보냈다")
        assertNotNull(revenue.unavailableReason)
        // 다른 지표는 살아 있어야 한다.
        assertNotNull(metric("VIEWS").currentValue)
    }

    @Test
    @DisplayName("실측 수익 행이 있으면 수익을 낸다")
    fun measuredRevenueIsReported() {
        given(
            listOf(Platform.YOUTUBE),
            revenueCounts = listOf(PlatformRevenueStatusCount("YOUTUBE", RevenueStatus.MEASURED.name, 2L)),
        )

        assertEquals(9_000_000L, metric("REVENUE").currentValue)
    }

    // ── 알림의 하드코딩 0 ────────────────────────────────────────────────────

    /**
     * **`LiveAlert` 도메인에는 값도 임계값도 없다**(`LiveAlert.kt` — type·message·severity 뿐).
     * 예전에는 `0L` 을 하드코딩해 내려보냈고, 화면이 그리면 "조회수 0 이 임계값 0 을
     * 넘었다" 는 말이 된다.
     */
    @Test
    @DisplayName("알림의 값과 임계값은 도메인에 없으므로 null 이다")
    fun alertValueAndThresholdAreNullNotZero() {
        given(
            listOf(Platform.YOUTUBE),
            alerts = listOf(LiveAlert(id = 1L, userId = userId, type = "SPIKE", message = "조회수 급등")),
        )

        val alert = useCase.getLiveState(userId).alerts.single()

        assertNull(alert.value, "없는 값을 0 으로 지어냈다")
        assertNull(alert.threshold, "없는 임계값을 0 으로 지어냈다")
        // 실제로 있는 정보는 그대로 남는다.
        assertEquals("조회수 급등", alert.description)
    }
}
