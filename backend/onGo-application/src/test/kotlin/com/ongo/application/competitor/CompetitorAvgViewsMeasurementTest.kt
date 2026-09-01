package com.ongo.application.competitor

import com.ongo.application.competitor.dto.CompetitorTrendRequest
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.competitor.ChannelLookupPort
import com.ongo.domain.competitor.Competitor
import com.ongo.domain.competitor.CompetitorAnalyticsDaily
import com.ongo.domain.competitor.CompetitorRepository
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 경쟁 채널의 **영상당 평균 조회수**가 미측정을 0 으로 위장하지 않는지 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * val avgViews = if (result.videoCount > 0) result.totalViews / result.videoCount else 0
 * ```
 *
 * `CompetitorRefreshService` 는 영상이 0 건이면 `0` 을 저장한다. 평균은 **분모가 없으면
 * 정의되지 않는데**, 그 `0` 이 응답에 그대로 실려 카드가 "평균 조회수 0회" 를 그렸다 —
 * 영상이 있고 조회수가 실제로 0 인 채널과 완전히 같은 모양이다.
 *
 * ## 지금의 계약
 *
 * 저장 모델(`Competitor.avgViews: Long`)은 그대로 두고(**스키마 변경 없음**), 응답
 * 경계에서 `videoCount` 를 근거로 갈라낸다.
 *
 * - `totalViews == null` → `null` (분자를 **모른다**)
 * - `videoCount == null` → `null` (분모를 **모른다**)
 * - `videoCount == 0` → `null` (분모 없음)
 * - `videoCount > 0` → 저장값 그대로. `totalViews` 가 0 이면 `0` 은 **실측 평균**이다.
 */
class CompetitorAvgViewsMeasurementTest {

    private val competitorRepository = mockk<CompetitorRepository>()
    private val analyticsRepository = mockk<AnalyticsRepository>(relaxed = true)
    private val channelRepository = mockk<ChannelRepository>(relaxed = true)

    private val useCase = CompetitorUseCase(
        competitorRepository = competitorRepository,
        channelLookupPort = mockk<ChannelLookupPort>(relaxed = true),
        competitorRefreshService = mockk<CompetitorRefreshService>(relaxed = true),
        analyticsRepository = analyticsRepository,
        videoUploadRepository = mockk<VideoUploadRepository>(relaxed = true),
        channelRepository = channelRepository,
        subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true),
    )

    private val userId = 7L

    private fun competitor(videoCount: Int?, avgViews: Long, totalViews: Long? = 0) = Competitor(
        id = 5L,
        userId = userId,
        platform = "YOUTUBE",
        platformChannelId = "rival",
        channelName = "경쟁 채널",
        subscriberCount = 10_000,
        totalViews = totalViews,
        videoCount = videoCount,
        avgViews = avgViews,
    )

    private fun givenCompetitor(competitor: Competitor) {
        every { competitorRepository.findByUserId(userId) } returns listOf(competitor)
        every { competitorRepository.countByUserId(userId) } returns 1
        every {
            competitorRepository.findAnalyticsByCompetitorIdAndDateRange(5L, any(), any())
        } returns emptyList()
    }

    // ══ 목록 응답 ═══════════════════════════════════════════════════════════

    /** **이 케이스가 "평균 조회수 0회" 를 카드에 그리던 자리다.** */
    @Test
    @DisplayName("영상이 0건이면 평균 조회수를 만들지 않는다")
    fun zeroVideoCountProducesNullAvgViews() {
        givenCompetitor(competitor(videoCount = 0, avgViews = 0))

        val response = useCase.listCompetitors(userId).competitors.single()

        assertNull(response.avgViews, "분모가 없는데 평균 0 회를 내보냈다")
        // 영상 수 자체는 실제 관측이므로 그대로 둔다.
        assertEquals(0, response.videoCount)
    }

    /** **영상이 있고 조회수가 실제 0 이면 그 평균 0 은 관측이다.** */
    /**
     * **분모를 모르는 것과 분모가 0 인 것은 다른 사실이지만, 평균은 둘 다 만들 수 없다.**
     *
     * 영상 수를 재지 못한 채널(조회 응답에 필드가 없거나 수동 입력에서 비워 둔 경우)은
     * `videoCount == null` 이다. 저장된 `avgViews` 는 계산하지 못한 자리의 0 이므로
     * 그대로 내보내면 "평균 0회" 라는 관측이 된다.
     */
    @Test
    @DisplayName("영상 수를 모르면 평균 조회수를 만들지 않는다")
    fun unknownVideoCountProducesNullAvgViews() {
        givenCompetitor(competitor(videoCount = null, avgViews = 0, totalViews = 1_000))

        val response = useCase.listCompetitors(userId).competitors.single()

        assertNull(response.avgViews, "분모를 모르는데 평균을 만들었다")
        assertNull(response.videoCount, "재지 못한 영상 수를 0 으로 냈다")
    }

    /**
     * **분자를 모르면 평균이 성립하지 않는다.**
     *
     * 조회 응답에 `viewCount` 가 없으면 `totalViews` 가 `null` 이다. 영상 수는 알아도
     * 평균의 근거가 없으므로 저장된 0 을 그대로 내보내면 "평균 0회" 라는 관측이 된다.
     */
    @Test
    @DisplayName("총 조회수를 모르면 평균 조회수를 만들지 않는다")
    fun unknownTotalViewsProducesNullAvgViews() {
        givenCompetitor(competitor(videoCount = 12, avgViews = 0, totalViews = null))

        val response = useCase.listCompetitors(userId).competitors.single()

        assertNull(response.avgViews, "분자를 모르는데 평균을 만들었다")
        assertNull(response.totalViews, "재지 못한 총 조회수를 0 으로 냈다")
        assertEquals(12, response.videoCount, "측정된 영상 수까지 잃었다")
    }

    /** 추이 스냅샷도 같은 계약이다. */
    @Test
    @DisplayName("추이도 그날 영상 수를 모르면 평균을 만들지 않는다")
    fun trendUnknownVideoCountProducesNullAvgViews() {
        every { competitorRepository.findByUserId(userId) } returns listOf(competitor(12, 1_500))
        every {
            competitorRepository.findAnalyticsByCompetitorIdAndDateRange(5L, any(), any())
        } returns listOf(snapshot(videoCount = null, avgViews = 0))

        val point = useCase.getCompetitorTrends(userId, CompetitorTrendRequest(days = 30)).single().data.single()

        assertNull(point.avgViews, "분모를 모르는데 평균을 만들었다")
    }

    @Test
    @DisplayName("영상이 있고 조회수가 0이면 평균 0을 유지한다")
    fun measuredZeroAvgViewsIsPreserved() {
        givenCompetitor(competitor(videoCount = 12, avgViews = 0, totalViews = 0))

        val response = useCase.listCompetitors(userId).competitors.single()

        assertEquals(0L, response.avgViews, "실측 평균 0 을 미측정으로 감췄다")
    }

    @Test
    @DisplayName("측정된 평균 조회수는 그대로 낸다")
    fun measuredAvgViewsPassThrough() {
        givenCompetitor(competitor(videoCount = 12, avgViews = 1_500, totalViews = 18_000))

        assertEquals(1_500L, useCase.listCompetitors(userId).competitors.single().avgViews)
    }

    // ══ 추이(trend) ═════════════════════════════════════════════════════════

    /**
     * @param totalViews 평균의 **분자**. 기본값은 측정된 값이다 — 비워 두면 `null` 이 되어
     *   분모와 무관하게 평균이 `null` 이 되고, 분모를 검증하려는 케이스가 그 이유로 걸린다.
     */
    private fun snapshot(videoCount: Int?, avgViews: Long, totalViews: Long? = 1_000L) =
        CompetitorAnalyticsDaily(
            competitorId = 5L,
            date = LocalDate.now().minusDays(1),
            subscriberCount = 10_000,
            videoCount = videoCount,
            avgViews = avgViews,
            totalViews = totalViews,
        )

    /** 추이 그래프에 0 점을 찍으면 "그날 평균 0회" 라는 관측이 된다. */
    @Test
    @DisplayName("추이도 그날 영상이 0건이면 평균을 만들지 않는다")
    fun trendZeroVideoCountProducesNullAvgViews() {
        every { competitorRepository.findByUserId(userId) } returns listOf(competitor(0, 0))
        every {
            competitorRepository.findAnalyticsByCompetitorIdAndDateRange(5L, any(), any())
        } returns listOf(snapshot(videoCount = 0, avgViews = 0))

        val point = useCase.getCompetitorTrends(userId, CompetitorTrendRequest(days = 30)).single().data.single()

        assertNull(point.avgViews, "분모가 없는데 평균 0 회를 내보냈다")
    }

    /** 추이도 분자를 모르면 평균을 만들지 않는다. */
    @Test
    @DisplayName("추이도 그날 총 조회수를 모르면 평균을 만들지 않는다")
    fun trendUnknownTotalViewsProducesNullAvgViews() {
        every { competitorRepository.findByUserId(userId) } returns listOf(competitor(12, 1_500))
        every {
            competitorRepository.findAnalyticsByCompetitorIdAndDateRange(5L, any(), any())
        } returns listOf(snapshot(videoCount = 3, avgViews = 0, totalViews = null))

        val point = useCase.getCompetitorTrends(userId, CompetitorTrendRequest(days = 30)).single().data.single()

        assertNull(point.avgViews, "분자를 모르는데 평균을 만들었다")
        assertNull(point.totalViews, "재지 못한 총 조회수를 0 으로 냈다")
    }

    @Test
    @DisplayName("추이의 실측 평균 0은 0으로 유지한다")
    fun trendMeasuredZeroIsPreserved() {
        every { competitorRepository.findByUserId(userId) } returns listOf(competitor(3, 0))
        every {
            competitorRepository.findAnalyticsByCompetitorIdAndDateRange(5L, any(), any())
        } returns listOf(snapshot(videoCount = 3, avgViews = 0))

        assertEquals(0L, useCase.getCompetitorTrends(userId, CompetitorTrendRequest(days = 30)).single().data.single().avgViews)
    }

    // ══ 벤치마크 ════════════════════════════════════════════════════════════

    /** 벤치마크는 경쟁자 평균을 그대로 재주입한다 — 여기서도 0 으로 되살아나면 안 된다. */
    @Test
    @DisplayName("벤치마크도 영상 0건이면 평균을 만들지 않는다")
    fun benchmarkZeroVideoCountProducesNullAvgViews() {
        every { channelRepository.findByUserId(userId) } returns emptyList()
        every { analyticsRepository.findAllByUserId(userId) } returns emptyList()
        givenCompetitor(competitor(videoCount = 0, avgViews = 0))

        val benchmark = useCase.getBenchmark(userId).competitors.single()

        assertNull(benchmark.avgViews, "분모가 없는데 평균 0 회를 내보냈다")
        // 성장률도 관측이 없으면 만들지 않는다(기존 계약).
        assertNull(benchmark.growthRate)
    }

    @Test
    @DisplayName("벤치마크의 실측 평균 0은 0으로 유지한다")
    fun benchmarkMeasuredZeroIsPreserved() {
        every { channelRepository.findByUserId(userId) } returns emptyList()
        every { analyticsRepository.findAllByUserId(userId) } returns emptyList()
        givenCompetitor(competitor(videoCount = 8, avgViews = 0))

        assertEquals(0L, useCase.getBenchmark(userId).competitors.single().avgViews)
    }
}
