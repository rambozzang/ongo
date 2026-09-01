package com.ongo.application.competitor

import com.ongo.common.enums.Platform
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
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
 * 경쟁사 벤치마크의 **내 채널 기준값**이 미수집 지표로 오염되지 않는지 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * val myTotalViews = allAnalytics.sumOf { it.views.toLong() }
 * val totalEngagements = allAnalytics.sumOf { (it.likes + it.commentsCount + it.shares).toLong() }
 * val myGrowthSubscribers = recentAnalytics.sumOf { it.subscriberGained }
 * ```
 *
 * `findAllByUserId` 가 주는 `AnalyticsDaily` 에는 `videoUploadId` 만 있어 플랫폼을 알 수
 * 없다. 그래서 필터 없이 더했고 다음이 섞였다.
 *
 * - `TumblrClient.kt:141` 의 `total_notes`(노트 총합) → 조회수
 * - `PinterestClient.kt:158/160` 의 `SAVE`(저장)·`PIN_CLICK`(클릭) → 참여 수
 * - `subscriber_gained` 는 YouTube 만 수집하는데 전 플랫폼 합산
 *
 * **이 값은 경쟁사와 나란히 놓이는 비교 기준**이라 오염되면 비교 결과가 통째로 틀린다.
 */
class BenchmarkPlatformFilterTest {

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

    /** @param uploads (uploadId, 플랫폼) — 그 업로드의 집계 행 값은 [rows] 로 준다. */
    private fun given(
        uploads: List<Pair<Long, Platform>>,
        rows: List<AnalyticsDaily>,
        subscriberCount: Long = 1_000,
    ) {
        every { competitorRepository.findByUserId(userId) } returns emptyList()
        every { channelRepository.findByUserId(userId) } returns listOf(
            Channel(
                id = 1L,
                userId = userId,
                platform = Platform.YOUTUBE,
                platformChannelId = "c1",
                channelName = "내 채널",
                subscriberCount = subscriberCount,
                accessToken = com.ongo.domain.channel.EncryptedToken("token"),
            ),
        )
        every { videoUploadRepository.findByUserId(userId) } returns uploads.map { (id, platform) ->
            VideoUpload(id = id, videoId = id, platform = platform, channelId = 1L)
        }
        every { analyticsRepository.findAllByUserId(userId) } returns rows
    }

    private fun row(
        uploadId: Long,
        views: Int = 0,
        likes: Int = 0,
        comments: Int = 0,
        shares: Int = 0,
        subscriberGained: Int = 0,
        daysAgo: Long = 3,
    ) = AnalyticsDaily(
        videoUploadId = uploadId,
        date = LocalDate.now().minusDays(daysAgo),
        views = views,
        likes = likes,
        commentsCount = comments,
        shares = shares,
        subscriberGained = subscriberGained,
    )

    private fun myStats() = useCase.getBenchmark(userId).myStats

    // ── 조회수 ───────────────────────────────────────────────────────────────

    /** **이 케이스가 노트 총합을 내 조회수로 쓰던 자리다.** */
    @Test
    @DisplayName("Tumblr 노트 총합을 내 총 조회수에 더하지 않는다")
    fun tumblrNoteCountIsNotMyViews() {
        given(
            uploads = listOf(11L to Platform.YOUTUBE, 12L to Platform.TUMBLR),
            rows = listOf(row(11L, views = 1_000), row(12L, views = 900_000)),
        )

        assertEquals(1_000L, myStats().totalViews, "노트 총합이 내 조회수에 섞였다")
    }

    @Test
    @DisplayName("조회수를 수집하는 업로드가 없으면 평균을 만들지 않는다")
    fun noViewReportingUploadProducesNullAverage() {
        given(
            uploads = listOf(11L to Platform.TUMBLR),
            rows = listOf(row(11L, views = 900_000)),
        )

        assertNull(myStats().avgViews, "근거 없는 평균 조회수를 만들었다")
    }

    // ── 참여율 ───────────────────────────────────────────────────────────────

    /** Pinterest 는 좋아요·댓글·공유를 하나도 주지 않는다 — 참여율 분자가 없다. */
    @Test
    @DisplayName("Pinterest 저장·클릭 수를 내 참여율에 더하지 않는다")
    fun pinterestMappedMetricsAreNotMyEngagement() {
        given(
            uploads = listOf(11L to Platform.PINTEREST),
            rows = listOf(row(11L, views = 1_000, likes = 300, shares = 400)),
        )

        assertNull(myStats().engagementRate, "저장·클릭 수로 참여율을 계산했다")
    }

    @Test
    @DisplayName("YouTube 행만으로 참여율을 계산한다")
    fun engagementUsesOnlyFullyReportingRows() {
        given(
            uploads = listOf(11L to Platform.YOUTUBE, 12L to Platform.PINTEREST),
            rows = listOf(
                row(11L, views = 1_000, likes = 50, comments = 30, shares = 20),
                row(12L, views = 9_000, likes = 900, shares = 900),
            ),
        )

        // YouTube 행만: (50+30+20)/1000 = 10.0%
        assertEquals(10.0, myStats().engagementRate, "Pinterest 행이 참여율에 섞였다")
    }

    /** **측정된 0 은 관측이다.** 조회는 있고 참여가 없었다는 뜻이다. */
    @Test
    @DisplayName("측정된 참여율 0%는 그대로 보존한다")
    fun measuredZeroEngagementIsPreserved() {
        given(
            uploads = listOf(11L to Platform.YOUTUBE),
            rows = listOf(row(11L, views = 1_000, likes = 0, comments = 0, shares = 0)),
        )

        assertEquals(0.0, myStats().engagementRate, "실측 0% 를 미측정으로 감췄다")
    }

    // ── 구독 성장률 ──────────────────────────────────────────────────────────

    /** `subscriber_gained` 를 조회하는 어댑터는 YouTube 하나뿐이다. */
    @Test
    @DisplayName("구독 증가를 수집하는 플랫폼이 없으면 성장률을 만들지 않는다")
    fun noSubscriberPlatformProducesNullGrowth() {
        given(
            uploads = listOf(11L to Platform.TIKTOK),
            rows = listOf(row(11L, views = 1_000, subscriberGained = 0)),
        )

        assertNull(myStats().growthRate, "물어볼 곳이 없는데 성장률 0% 를 냈다")
    }

    @Test
    @DisplayName("YouTube 가 있으면 성장률을 계산한다")
    fun youtubeProducesGrowthRate() {
        given(
            uploads = listOf(11L to Platform.YOUTUBE),
            rows = listOf(row(11L, views = 1_000, subscriberGained = 50)),
            subscriberCount = 1_000,
        )

        // 50 / 1,000 = 5.0%
        assertEquals(5.0, myStats().growthRate)
    }

    /** **측정된 0 은 관측이다.** YouTube 행이 있고 실제로 늘지 않았다는 뜻이다. */
    @Test
    @DisplayName("YouTube 행의 구독 증가 0은 성장률 0%로 보존한다")
    fun measuredZeroGrowthIsPreserved() {
        given(
            uploads = listOf(11L to Platform.YOUTUBE),
            rows = listOf(row(11L, views = 1_000, subscriberGained = 0)),
        )

        assertEquals(0.0, myStats().growthRate, "실측 0% 를 미측정으로 감췄다")
    }
}
