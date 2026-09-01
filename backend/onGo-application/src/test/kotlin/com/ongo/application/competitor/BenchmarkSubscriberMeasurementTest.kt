package com.ongo.application.competitor

import com.ongo.common.enums.Platform
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.competitor.ChannelLookupPort
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
 * 벤치마크의 **내 채널 구독자 수**가 재지 않은 채널을 0 명으로 세지 않는지 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * val mySubscribers = channels.sumOf { it.subscriberCount }
 * ```
 *
 * `ThreadsClient.kt:205` 와 `LinkedInClient.kt:264` 는 팔로워 수를 **묻지도 않고**
 * `subscriberCount = 0` 을 박아 넣는다(`NaverClipClient.kt:40` 은 조회 자체가 예외다).
 * 그 두 플랫폼만 연동한 크리에이터는 합계가 항상 `0` 이었고, `MyChannelStats.subscriberCount`
 * 가 `Long` non-null 이라 **비교표에 "구독자 0명"** 이 측정 결과로 그려졌다.
 *
 * 같은 DTO 의 `totalViews`·`avgViews` 는 이미 nullable 계약인데 이 필드만 어긋나 있었다.
 *
 * **실측 0 은 보존한다** — 조회하는 플랫폼의 채널이 실제로 0 명인 것은 관측이다.
 */
class BenchmarkSubscriberMeasurementTest {

    private val competitorRepository = mockk<CompetitorRepository>(relaxed = true)
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

    private fun channel(platform: Platform, subscribers: Long) = Channel(
        userId = userId,
        platform = platform,
        platformChannelId = "ch-${platform.name}",
        channelName = platform.name,
        subscriberCount = subscribers,
        accessToken = EncryptedToken("token"),
    )

    /**
     * @param rows 구독 증가 집계 행. 성장률 분모가 구독자 수라서 함께 본다.
     */
    private fun given(channels: List<Channel>, rows: List<AnalyticsDaily> = emptyList()) {
        every { competitorRepository.findByUserId(userId) } returns emptyList()
        every { channelRepository.findByUserId(userId) } returns channels
        every { videoUploadRepository.findByUserId(userId) } returns listOf(
            VideoUpload(id = 11L, videoId = 11L, platform = Platform.YOUTUBE, channelId = 1L),
        )
        every { analyticsRepository.findAllByUserId(userId) } returns rows
    }

    private fun mySubscribers() = useCase.getBenchmark(userId).myStats.subscriberCount

    // ── 재지 않은 채널 ───────────────────────────────────────────────────────

    /** **이 케이스가 비교표에 "구독자 0명" 을 그리던 자리다.** */
    @Test
    @DisplayName("Threads·LinkedIn 만 연동했으면 구독자 수가 null 이다")
    fun unmeasuredOnlyChannelsProduceNull() {
        given(listOf(channel(Platform.THREADS, 0), channel(Platform.LINKEDIN, 0)))

        assertNull(mySubscribers(), "재지 않은 채널을 0 명으로 셌다")
    }

    @Test
    @DisplayName("연동 채널이 없으면 구독자 수가 null 이다")
    fun noChannelsProduceNull() {
        given(emptyList())

        assertNull(mySubscribers())
    }

    // ── 실측 보존 ────────────────────────────────────────────────────────────

    /** **조회하는 플랫폼의 0 은 관측이다.** 갓 만든 채널의 구독자 0 명. */
    @Test
    @DisplayName("조회하는 플랫폼의 실측 0 은 0 으로 남긴다")
    fun measuredZeroIsPreserved() {
        given(listOf(channel(Platform.YOUTUBE, 0)))

        assertEquals(0L, mySubscribers(), "실측 0 을 미측정으로 감췄다")
    }

    @Test
    @DisplayName("측정된 구독자 수는 그대로 낸다")
    fun measuredSubscribersArePreserved() {
        given(listOf(channel(Platform.YOUTUBE, 8_000)))

        assertEquals(8_000L, mySubscribers())
    }

    /** 미측정 채널이 섞여도 **잰 채널의 합계는 그대로**다. */
    @Test
    @DisplayName("측정·미측정이 섞이면 측정된 합만 낸다")
    fun mixedChannelsKeepOnlyTheMeasuredSum() {
        given(
            listOf(
                channel(Platform.YOUTUBE, 1_000),
                channel(Platform.INSTAGRAM, 500),
                // 아래 둘은 잰 적이 없다 — 0 을 더해 합계를 낮추면 안 된다.
                channel(Platform.THREADS, 0),
                channel(Platform.LINKEDIN, 0),
            ),
        )

        assertEquals(1_500L, mySubscribers())
    }

    // ── 성장률 계산부가 깨지지 않는가 ────────────────────────────────────────
    //
    // `myGrowthRate` 는 구독 증가분을 `mySubscribers` 로 나눈다. 분모가 nullable 이 됐으니
    // 그 자리에서 재지 않은 0 을 쓰지 않는지 함께 고정한다.

    private fun growthRow(subscriberGained: Int) = AnalyticsDaily(
        videoUploadId = 11L,
        date = LocalDate.now().minusDays(3),
        views = 100,
        subscriberGained = subscriberGained,
    )

    /** 분모가 없으면 비율이 성립하지 않는다. */
    @Test
    @DisplayName("구독자 수가 미측정이면 성장률을 만들지 않는다")
    fun growthRateIsNullWhenSubscribersAreUnmeasured() {
        given(
            channels = listOf(channel(Platform.THREADS, 0)),
            rows = listOf(growthRow(50)),
        )

        assertNull(useCase.getBenchmark(userId).myStats.growthRate, "재지 않은 구독자 수를 분모로 썼다")
    }

    /** **측정된 구독자 수가 있으면 성장률은 그대로 계산된다.** 과도한 차단 회귀를 막는다. */
    @Test
    @DisplayName("측정된 구독자 수로는 성장률을 계산한다")
    fun growthRateIsComputedFromMeasuredSubscribers() {
        given(
            channels = listOf(channel(Platform.YOUTUBE, 1_000)),
            rows = listOf(growthRow(50)),
        )

        // 50 / 1000 * 100 = 5.0
        assertEquals(5.0, useCase.getBenchmark(userId).myStats.growthRate)
    }
}
