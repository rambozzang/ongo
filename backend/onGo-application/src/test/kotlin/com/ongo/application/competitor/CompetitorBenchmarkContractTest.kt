package com.ongo.application.competitor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.competitor.ChannelLookupPort
import com.ongo.domain.competitor.Competitor
import com.ongo.domain.competitor.CompetitorRepository
import com.ongo.domain.subscription.SubscriptionRepository
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
 * 벤치마크 응답이 **측정할 수 없는 값을 숫자로 내보내지 않는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * 경쟁자 참여율은 좋아요·댓글·공유를 조회수로 나눈 값인데, 우리가 남의 채널에 대해 공개
 * API 로 얻는 것은 구독자 수·총 조회수·영상 수뿐이다. **분자가 없다.**
 *
 * 그런데 코드는 그 자리에 `0.0` 을 넣었다. 주석에는 "정확히 알 수 없음"이라고 적혀
 * 있었지만 값은 측정값과 구분되지 않는 모양으로 나갔고, 프론트 비교표가 그대로
 * **"참여율: 나 4.2% vs 경쟁자 0.0%"** 를 그렸다. 크리에이터는 추적하는 **모든** 경쟁사를
 * 참여율에서 압도한다고 믿게 된다 — 존재하지 않는 경쟁 우위를 근거로 전략을 세운다.
 *
 * `null` 은 "0 이다" 가 아니라 "모른다" 다. 대시보드 증감률([MetricChange])과
 * 플랫폼 지표 가용성([PlatformMetricAvailability])이 쓰는 것과 같은 정책이다.
 */
class CompetitorBenchmarkContractTest {

    private val competitorRepository = mockk<CompetitorRepository>()
    private val channelLookupPort = mockk<ChannelLookupPort>(relaxed = true)
    private val competitorRefreshService = mockk<CompetitorRefreshService>(relaxed = true)
    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val channelRepository = mockk<ChannelRepository>(relaxed = true)
    private val subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true)

    /**
     * 집계 행의 플랫폼을 알려면 업로드 매핑이 필요하다. 이 테스트는 경쟁사 쪽 계약만
     * 보므로 내 채널 업로드는 비워 둔다 — 그러면 내 지표는 미측정으로 나온다.
     */
    private val videoUploadRepository = mockk<com.ongo.domain.video.VideoUploadRepository>(relaxed = true)

    private val useCase = CompetitorUseCase(
        competitorRepository = competitorRepository,
        channelLookupPort = channelLookupPort,
        competitorRefreshService = competitorRefreshService,
        analyticsRepository = analyticsRepository,
        videoUploadRepository = videoUploadRepository,
        channelRepository = channelRepository,
        subscriptionRepository = subscriptionRepository,
    )

    private val userId = 7L

    private fun givenCompetitors(vararg competitors: Competitor) {
        every { competitorRepository.findByUserId(userId) } returns competitors.toList()
        every { channelRepository.findByUserId(userId) } returns emptyList()
        // 성장률 산출용 스냅샷. 참여율과 무관하며, 없으면 성장률만 0 이 된다.
        every {
            competitorRepository.findAnalyticsByCompetitorIdAndDateRange(any(), any(), any())
        } returns emptyList()
    }

    /**
     * 내 채널은 우리 DB 에 좋아요·댓글이 있다. **실제로 측정된다.**
     *
     * 업로드를 YouTube 로 등록한다 — 집계 행에는 `videoUploadId` 만 있어, 이 매핑이 없으면
     * 플랫폼을 알 수 없어 fail-closed 로 전부 빠진다.
     */
    private fun givenMyAnalytics(views: Int, likes: Int, comments: Int, shares: Int) {
        every { analyticsRepository.findAllByUserId(userId) } returns listOf(
            AnalyticsDaily(
                id = 1,
                videoUploadId = 11,
                date = LocalDate.now().minusDays(3),
                views = views,
                likes = likes,
                commentsCount = comments,
                shares = shares,
            ),
        )
        every { videoUploadRepository.findByUserId(userId) } returns listOf(
            com.ongo.domain.video.VideoUpload(
                id = 11,
                videoId = 1,
                platform = com.ongo.common.enums.Platform.YOUTUBE,
                channelId = 1,
            ),
        )
    }

    private fun competitor(id: Long, name: String) = Competitor(
        id = id,
        userId = userId,
        platform = "YOUTUBE",
        platformChannelId = "ch-$id",
        channelName = name,
        subscriberCount = 10_000,
        totalViews = 500_000,
        videoCount = 50,
        avgViews = 10_000,
    )

    // ── 경쟁자 참여율은 측정 불가 ────────────────────────────────────────────

    /** **이 케이스가 "경쟁자 0%" 를 만들던 자리다.** */
    @Test
    @DisplayName("경쟁자 참여율은 0이 아니라 null이다")
    fun competitorEngagementRateIsNullNotZero() {
        givenCompetitors(competitor(1, "경쟁 채널"))
        givenMyAnalytics(views = 1000, likes = 30, comments = 10, shares = 2)

        val response = useCase.getBenchmark(userId)

        val rival = response.competitors.single()
        assertNull(rival.engagementRate, "산출할 수 없는 값을 0.0 으로 내보냈다")
        assertNotNull(
            rival.engagementRateUnavailableReason,
            "왜 없는지 알려주지 않으면 '아직 안 불러왔다' 와 구분되지 않는다",
        )
    }

    /** 이유 문장이 비어 있으면 화면이 보여줄 것이 없다. */
    @Test
    @DisplayName("측정 불가 사유는 사람이 읽을 수 있는 문장이다")
    fun unavailableReasonIsHumanReadable() {
        givenCompetitors(competitor(1, "경쟁 채널"))
        givenMyAnalytics(views = 1000, likes = 30, comments = 10, shares = 2)

        val reason = useCase.getBenchmark(userId).competitors.single().engagementRateUnavailableReason

        assertEquals(CompetitorUseCase.COMPETITOR_ENGAGEMENT_UNAVAILABLE, reason)
        assertTrue(reason!!.contains("참여율"), "무엇을 못 구하는지 알려주지 않는다: $reason")
    }

    /** 경쟁자가 여럿이어도 전부 같은 정책이다. 하나라도 0 이면 비교표가 다시 거짓말한다. */
    @Test
    @DisplayName("경쟁자가 여럿이어도 전부 측정 불가다")
    fun everyCompetitorReportsUnavailable() {
        givenCompetitors(competitor(1, "A"), competitor(2, "B"), competitor(3, "C"))
        givenMyAnalytics(views = 1000, likes = 30, comments = 10, shares = 2)

        val competitors = useCase.getBenchmark(userId).competitors

        assertEquals(3, competitors.size)
        assertTrue(
            competitors.all { it.engagementRate == null },
            "0 으로 나간 경쟁자가 있다: ${competitors.filter { it.engagementRate != null }.map { it.channelName }}",
        )
    }

    // ── 내 채널 값은 그대로 ──────────────────────────────────────────────────

    /**
     * 내 채널 참여율은 우리 DB 의 좋아요·댓글·공유로 **실제 계산된다.** 이번 변경이
     * 그것까지 비활성화하면 화면에서 비교 자체가 사라진다.
     */
    @Test
    @DisplayName("내 채널 참여율은 실제 측정값으로 남는다")
    fun myEngagementRateStaysMeasured() {
        givenCompetitors(competitor(1, "경쟁 채널"))
        // (30 + 10 + 2) / 1000 * 100 = 4.2%
        givenMyAnalytics(views = 1000, likes = 30, comments = 10, shares = 2)

        val myStats = useCase.getBenchmark(userId).myStats

        assertEquals(4.2, myStats.engagementRate)
    }

    /** 경쟁자가 없어도 내 통계는 그대로 나와야 한다. */
    @Test
    @DisplayName("경쟁자가 없어도 내 통계는 계산된다")
    fun myStatsSurviveWithoutCompetitors() {
        givenCompetitors()
        givenMyAnalytics(views = 1000, likes = 30, comments = 10, shares = 2)

        val response = useCase.getBenchmark(userId)

        assertTrue(response.competitors.isEmpty())
        assertEquals(4.2, response.myStats.engagementRate)
    }

    // ── API 계약 (직렬화) ────────────────────────────────────────────────────

    /**
     * 프론트 소비자가 이 JSON 을 그대로 읽는다. `null` 이 `0` 으로 나가면 서버에서
     * 아무리 정책을 지켜도 화면은 다시 "경쟁자 0%" 를 그린다.
     */
    @Test
    @DisplayName("JSON에서 경쟁자 참여율이 null로 나간다")
    fun jsonKeepsTheNull() {
        givenCompetitors(competitor(1, "경쟁 채널"))
        givenMyAnalytics(views = 1000, likes = 30, comments = 10, shares = 2)

        val json = jacksonObjectMapper().writeValueAsString(useCase.getBenchmark(userId))

        assertTrue(json.contains("\"engagementRate\":null"), "경쟁자 참여율이 null 이 아니다:\n$json")
        assertTrue(
            json.contains("\"engagementRateUnavailableReason\":\""),
            "측정 불가 사유가 직렬화되지 않았다:\n$json",
        )
        // 내 값은 숫자로 살아 있어야 한다.
        assertTrue(json.contains("\"engagementRate\":4.2"), "내 참여율이 사라졌다:\n$json")
    }
}
