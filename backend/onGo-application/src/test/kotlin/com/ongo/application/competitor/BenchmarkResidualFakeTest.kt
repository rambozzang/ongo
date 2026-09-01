package com.ongo.application.competitor

import com.ongo.common.enums.Platform
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.competitor.ChannelLookupPort
import com.ongo.domain.competitor.Competitor
import com.ongo.domain.competitor.CompetitorAnalyticsDaily
import com.ongo.domain.competitor.CompetitorRepository
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 벤치마크에 남아 있던 **두 가지 위장**을 고정한다.
 *
 * 1. `MyChannelStats.totalViews` 는 측정 행이 없어도 `0` 으로 나갔다. 같은 응답의
 *    `videoCount`·`avgViews` 는 이미 미측정을 구분하고 있었으므로 **한 객체 안에서
 *    두 필드가 서로 다른 계약**을 쓰고 있었다.
 * 2. 경쟁사 `growthRate` 는 기간 내 수집 이력이 없거나 기준일 구독자가 0 이어도
 *    `0.0` 을 만들었다. 화면은 그것을 "성장률 0%" 로 그렸고, 한 번도 수집한 적 없는
 *    경쟁사가 "정체 중" 으로 보였다.
 */
class BenchmarkResidualFakeTest {

    private val competitorRepository = mockk<CompetitorRepository>()
    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>()
    private val channelRepository = mockk<ChannelRepository>()

    private val useCase = CompetitorUseCase(
        competitorRepository = competitorRepository,
        channelLookupPort = mockk<ChannelLookupPort>(relaxed = true),
        competitorRefreshService = mockk<CompetitorRefreshService>(relaxed = true),
        analyticsRepository = analyticsRepository,
        videoUploadRepository = videoUploadRepository,
        channelRepository = channelRepository,
        subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true),
    )

    private val userId = 7L

    private fun givenMyChannel(uploadPlatform: Platform?, viewRows: List<AnalyticsDaily>) {
        every { channelRepository.findByUserId(userId) } returns listOf(
            Channel(
                id = 1L,
                userId = userId,
                platform = Platform.YOUTUBE,
                platformChannelId = "c1",
                channelName = "내 채널",
                subscriberCount = 1_000,
                accessToken = EncryptedToken("token"),
            ),
        )
        val uploads: List<VideoUpload> = uploadPlatform
            ?.let { listOf(VideoUpload(id = 11L, videoId = 1L, platform = it, channelId = 1L)) }
            ?: emptyList()
        every { videoUploadRepository.findByUserId(userId) } returns uploads
        every { analyticsRepository.findAllByUserId(userId) } returns viewRows
    }

    private fun row(views: Int) =
        AnalyticsDaily(videoUploadId = 11L, date = LocalDate.now().minusDays(3), views = views)

    // ══ 1) MyChannelStats.totalViews ════════════════════════════════════════

    /** **이 케이스가 "총 조회수 0" 을 관측처럼 내보내던 자리다.** */
    @Test
    @DisplayName("조회수 측정 행이 없으면 총 조회수를 만들지 않는다")
    fun noViewRowsProducesNullTotalViews() {
        every { competitorRepository.findByUserId(userId) } returns emptyList()
        givenMyChannel(uploadPlatform = Platform.TUMBLR, viewRows = listOf(row(900_000)))

        val stats = useCase.getBenchmark(userId).myStats

        assertNull(stats.totalViews, "측정 행이 없는데 0 을 관측처럼 내보냈다")
        // 같은 객체 안의 다른 필드와 계약이 일치해야 한다.
        assertNull(stats.avgViews)
        assertEquals(0, stats.videoCount)
    }

    /** **측정 행이 있고 합이 0 이면 그 0 은 실측이다.** */
    @Test
    @DisplayName("측정 행의 합계가 0이면 0을 보존한다")
    fun measuredZeroTotalViewsIsPreserved() {
        every { competitorRepository.findByUserId(userId) } returns emptyList()
        givenMyChannel(uploadPlatform = Platform.YOUTUBE, viewRows = listOf(row(0)))

        val stats = useCase.getBenchmark(userId).myStats

        assertEquals(0L, stats.totalViews, "실측 0 을 미측정으로 감췄다")
        assertEquals(0L, stats.avgViews)
        assertEquals(1, stats.videoCount)
    }

    @Test
    @DisplayName("측정된 조회수는 그대로 합산한다")
    fun measuredViewsAreSummed() {
        every { competitorRepository.findByUserId(userId) } returns emptyList()
        givenMyChannel(uploadPlatform = Platform.YOUTUBE, viewRows = listOf(row(600), row(400)))

        assertEquals(1_000L, useCase.getBenchmark(userId).myStats.totalViews)
    }

    // ══ 2) 경쟁사 growthRate ════════════════════════════════════════════════

    private fun givenCompetitor(vararg analytics: CompetitorAnalyticsDaily) {
        givenMyChannel(uploadPlatform = Platform.YOUTUBE, viewRows = listOf(row(100)))
        every { competitorRepository.findByUserId(userId) } returns listOf(
            Competitor(
                id = 5L,
                userId = userId,
                platform = "YOUTUBE",
                platformChannelId = "rival",
                channelName = "경쟁 채널",
                subscriberCount = 10_000,
            ),
        )
        every {
            competitorRepository.findAnalyticsByCompetitorIdAndDateRange(5L, any(), any())
        } returns analytics.toList()
    }

    private fun snapshot(daysAgo: Long, subscribers: Long) = CompetitorAnalyticsDaily(
        competitorId = 5L,
        date = LocalDate.now().minusDays(daysAgo),
        subscriberCount = subscribers,
    )

    private fun competitorGrowth() = useCase.getBenchmark(userId).competitors.single().growthRate

    /** **이 케이스가 "성장률 0%" 를 지어내던 자리다.** */
    @Test
    @DisplayName("기간 내 수집 이력이 없으면 성장률을 만들지 않는다")
    fun noHistoryProducesNullGrowth() {
        givenCompetitor()

        assertNull(competitorGrowth(), "수집한 적 없는 경쟁사를 '정체 중' 으로 그렸다")
    }

    /** 관측이 하나뿐이면 시작과 끝이 같은 행이라 변화를 잰 적이 없다. */
    @Test
    @DisplayName("관측이 하나뿐이면 성장률을 만들지 않는다")
    fun singleObservationProducesNullGrowth() {
        givenCompetitor(snapshot(daysAgo = 10, subscribers = 10_000))

        assertNull(competitorGrowth(), "한 시점만 보고 성장률을 말했다")
    }

    /** 기준일 구독자가 0 이면 비율의 분모가 없다. */
    @Test
    @DisplayName("기준일 구독자가 0이면 성장률을 만들지 않는다")
    fun zeroBaselineProducesNullGrowth() {
        givenCompetitor(
            snapshot(daysAgo = 20, subscribers = 0),
            snapshot(daysAgo = 1, subscribers = 500),
        )

        assertNull(competitorGrowth(), "0 을 기준으로 비율을 만들었다")
    }

    /** **두 시점이 관측됐고 값이 같으면 그 0% 는 실측이다.** */
    @Test
    @DisplayName("관측된 두 시점이 같으면 성장률 0%를 보존한다")
    fun measuredZeroGrowthIsPreserved() {
        givenCompetitor(
            snapshot(daysAgo = 20, subscribers = 10_000),
            snapshot(daysAgo = 1, subscribers = 10_000),
        )

        assertEquals(0.0, competitorGrowth(), "실측 0% 를 미측정으로 감췄다")
    }

    @Test
    @DisplayName("관측된 두 시점이 다르면 성장률을 계산한다")
    fun measuredGrowthIsComputed() {
        givenCompetitor(
            snapshot(daysAgo = 20, subscribers = 10_000),
            snapshot(daysAgo = 1, subscribers = 11_000),
        )

        // (11,000 - 10,000) / 10,000 = 10.0%
        assertEquals(10.0, competitorGrowth())
    }

    /** 감소도 관측이다 — 음수를 그대로 남긴다. */
    @Test
    @DisplayName("구독자가 줄면 음수 성장률을 그대로 낸다")
    fun measuredDeclineIsNegative() {
        givenCompetitor(
            snapshot(daysAgo = 20, subscribers = 10_000),
            snapshot(daysAgo = 1, subscribers = 9_000),
        )

        assertEquals(-10.0, competitorGrowth())
    }
}
