package com.ongo.application.channelaudit

import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.result.ChannelAuditResult
import com.ongo.application.credit.CreditAllocation
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.CrossPlatformRaw
import com.ongo.domain.analytics.DashboardKpi
import com.ongo.common.enums.Platform
import com.ongo.domain.analytics.MetricChange
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.channelaudit.ChannelAuditReport
import com.ongo.domain.channelaudit.ChannelAuditRepository
import com.ongo.domain.video.Video
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 채널 오디트(15 크레딧)가 **영상별 참여율을 지어내지 않는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * 프롬프트를 만드는 코드는 이랬다.
 *
 * ```
 * val videoPerformance = topVideos.mapIndexed { i, v ->
 *     val engagementRate = if (kpi.totalViews > 0) {
 *         String.format("%.2f", (kpi.totalLikes.toDouble() / kpi.totalViews) * 100)
 *     } else "0.00"
 *     "${i + 1}. ${v.title} — 참여율 ${engagementRate}%"
 * }
 * ```
 *
 * `engagementRate` 가 **`v` 를 전혀 쓰지 않는다.** 상위 10 개 영상 전부에 채널 전체 평균이
 * "그 영상의 참여율" 이라는 이름으로 붙었다. AI 는 열 개의 동일한 가짜 수치를 근거로
 * "어느 영상이 잘됐는지" 진단을 썼고, 사용자는 15 크레딧을 내고 그 조언을 받았다.
 *
 * 필요한 데이터는 이미 있었다 — `findCrossPlatformMetrics` 가 영상별 조회수·좋아요·
 * 댓글·공유를 준다. 같은 메서드가 플랫폼 요약을 만드느라 이미 부르고 있었다.
 */
class ChannelAuditVideoEngagementTest {

    private val chatClientResolver = mockk<ChatClientResolver>()
    private val creditService = mockk<CreditService>()
    private val rateLimiter = mockk<AiRateLimiter>(relaxed = true)
    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val channelRepository = mockk<ChannelRepository>()
    private val channelAuditRepository = mockk<ChannelAuditRepository>()

    private val useCase = ChannelAuditUseCase(
        chatClientResolver = chatClientResolver,
        creditService = creditService,
        rateLimiter = rateLimiter,
        analyticsRepository = analyticsRepository,
        channelRepository = channelRepository,
        channelAuditRepository = channelAuditRepository,
    )

    private val userId = 7L

    private fun row(videoId: Long, platform: String, views: Long, likes: Long, comments: Long, shares: Long) =
        CrossPlatformRaw(
            videoId = videoId,
            videoTitle = "영상 $videoId",
            platform = platform,
            videoUploadId = videoId * 10,
            views = views, likes = likes, comments = comments, shares = shares,
            watchTimeSeconds = 0, revenueMicro = 0, impressions = 0, avgViewDurationSeconds = 0,
        )

    /**
     * 유스케이스를 실제로 돌리고 AI 에게 간 user 프롬프트를 돌려준다.
     *
     * 채널 평균이 얼마든 **영상별 값과 섞이면 안 된다.** 그래서 KPI 를 일부러 눈에 띄는
     * 값(50% 참여율)으로 둔다 — 예전 코드였다면 모든 줄에 `50.00%` 가 찍혔다.
     */
    private fun renderedPrompt(
        crossPlatform: List<CrossPlatformRaw>,
        topVideos: List<Video>,
        channels: List<Channel> = emptyList(),
    ): String {
        every { channelRepository.findByUserId(userId) } returns channels
        every { analyticsRepository.getDashboardKpi(userId, 30) } returns DashboardKpi(
            totalViews = 1000, totalViewsChange = null,
            totalSubscribers = 100, totalSubscribersChange = 0,
            totalLikes = 500, totalLikesChange = null,
            creditBalance = 0, creditTotal = 0, totalComments = 0,
        )
        every { analyticsRepository.getTopVideos(userId, 30, 10) } returns topVideos
        every { analyticsRepository.findCrossPlatformMetrics(userId, 30) } returns crossPlatform
        every { creditService.withCredits(userId, AiFeature.CHANNEL_AUDIT, any<() -> Any>()) } answers {
            @Suppress("UNCHECKED_CAST")
            (thirdArg<() -> Any>()).invoke()
        }
        every { channelAuditRepository.save(any()) } answers {
            firstArg<ChannelAuditReport>().copy(id = 1)
        }

        val userPrompt = slot<String>()
        val requestSpec = mockk<ChatClient.ChatClientRequestSpec>()
        val callSpec = mockk<ChatClient.CallResponseSpec>()
        val chatClient = mockk<ChatClient>()
        every { chatClientResolver.resolve(userId) } returns chatClient
        every { chatClient.prompt() } returns requestSpec
        every { requestSpec.system(any<String>()) } returns requestSpec
        every { requestSpec.user(capture(userPrompt)) } returns requestSpec
        every { requestSpec.call() } returns callSpec
        every { callSpec.entity(ChannelAuditResult::class.java) } returns ChannelAuditResult(
            overallScore = 70,
            strengths = emptyList(),
            weaknesses = emptyList(),
            actionItems = emptyList(),
            outlierVideos = emptyList(),
            growthForecast = "예측",
        )

        useCase.generateAudit(userId)

        assertTrue(userPrompt.isCaptured, "AI user 프롬프트를 붙잡지 못했다")
        return userPrompt.captured
    }

    private fun video(id: Long) = Video(id = id, userId = userId, title = "영상 $id")

    // ── 영상별 실제 값 ───────────────────────────────────────────────────────

    /** **이 케이스가 열 줄을 같은 숫자로 만들던 자리다.** */
    @Test
    @DisplayName("영상마다 자기 데이터로 참여율을 계산한다")
    fun eachVideoUsesItsOwnMetrics() {
        val prompt = renderedPrompt(
            crossPlatform = listOf(
                row(1, "YOUTUBE", views = 1000, likes = 100, comments = 20, shares = 10), // 13.00%
                row(2, "YOUTUBE", views = 500, likes = 5, comments = 0, shares = 0), // 1.00%
            ),
            topVideos = listOf(video(1), video(2)),
        )

        assertTrue(prompt.contains("1. 영상 1 — 참여율 13.00%"), "영상 1 의 실제 값이 아니다:\n$prompt")
        assertTrue(prompt.contains("2. 영상 2 — 참여율 1.00%"), "영상 2 의 실제 값이 아니다:\n$prompt")
        // 채널 평균(500/1000 = 50%)이 새어 들어오면 안 된다.
        assertFalse(prompt.contains("50.00%"), "채널 평균이 영상별 값으로 복사됐다:\n$prompt")
    }

    /** 두 영상이 같은 숫자를 받는 것 자체가 예전 버그의 증상이었다. */
    @Test
    @DisplayName("서로 다른 성과의 영상은 서로 다른 값을 받는다")
    fun differentVideosGetDifferentRates() {
        val prompt = renderedPrompt(
            crossPlatform = listOf(
                row(1, "YOUTUBE", views = 1000, likes = 100, comments = 0, shares = 0),
                row(2, "YOUTUBE", views = 1000, likes = 10, comments = 0, shares = 0),
            ),
            topVideos = listOf(video(1), video(2)),
        )

        val rates = Regex("참여율 ([0-9.]+)%").findAll(prompt).map { it.groupValues[1] }.toList()
        assertEquals(listOf("10.00", "1.00"), rates, "영상별로 갈리지 않았다:\n$prompt")
    }

    /** 한 영상이 여러 플랫폼에 게시됐으면 합산한다. */
    @Test
    @DisplayName("멀티 플랫폼 게시는 합산해서 계산한다")
    fun multiPlatformRowsAreSummed() {
        val prompt = renderedPrompt(
            crossPlatform = listOf(
                row(1, "YOUTUBE", views = 600, likes = 60, comments = 0, shares = 0),
                row(1, "TIKTOK", views = 400, likes = 40, comments = 0, shares = 0),
            ),
            topVideos = listOf(video(1)),
        )

        // (60 + 40) / (600 + 400) = 10.00%
        assertTrue(prompt.contains("1. 영상 1 — 참여율 10.00%"), "플랫폼 합산이 틀렸다:\n$prompt")
    }

    /**
     * Facebook 은 공유 수를, Pinterest 는 댓글 수를 API 로 주지 않는다. 그 자리의 0 을
     * 분자에 더하면 참여율이 실제보다 낮게 나온다 — 대시보드 영상 비교와 같은 규칙이다.
     */
    @Test
    @DisplayName("플랫폼이 주지 않는 지표는 분자에서 제외한다")
    fun unavailableMetricsAreExcludedFromTheNumerator() {
        val prompt = renderedPrompt(
            crossPlatform = listOf(
                // Facebook: shares 는 미수집이므로 0 이 아니라 아예 세지 않는다.
                row(1, "FACEBOOK", views = 1000, likes = 80, comments = 20, shares = 0),
            ),
            topVideos = listOf(video(1)),
        )

        // (80 + 20) / 1000 = 10.00%
        assertTrue(prompt.contains("참여율 10.00%"), "미수집 공유가 분자에 섞였다:\n$prompt")
    }

    // ── 측정 불가 ────────────────────────────────────────────────────────────

    /** **분모 0 도 측정 불가다.** 0.00% 는 "참여가 없었다" 는 관측 결과로 읽힌다. */
    @Test
    @DisplayName("조회수가 0이면 0.00%가 아니라 측정 불가다")
    fun zeroViewsIsNotZeroPercent() {
        val prompt = renderedPrompt(
            crossPlatform = listOf(row(1, "YOUTUBE", views = 0, likes = 0, comments = 0, shares = 0)),
            topVideos = listOf(video(1)),
        )

        assertTrue(
            prompt.contains("1. 영상 1 — 참여율 ${ChannelAuditUseCase.ENGAGEMENT_UNAVAILABLE}"),
            "분모 0 을 0.00% 로 채웠다:\n$prompt",
        )
        assertFalse(prompt.contains("0.00%"), "0.00% 가 남았다:\n$prompt")
    }

    /** 분석 행이 아예 없는 영상. 제목과 순서는 유지하되 숫자를 만들지 않는다. */
    @Test
    @DisplayName("분석 데이터가 없는 영상은 측정 불가로 표시하되 목록에서 빼지 않는다")
    fun videoWithoutAnalyticsStaysListedAsUnavailable() {
        val prompt = renderedPrompt(
            crossPlatform = listOf(row(1, "YOUTUBE", views = 1000, likes = 100, comments = 0, shares = 0)),
            topVideos = listOf(video(1), video(2)),
        )

        assertTrue(prompt.contains("1. 영상 1 — 참여율 10.00%"))
        assertTrue(
            prompt.contains("2. 영상 2 — 참여율 ${ChannelAuditUseCase.ENGAGEMENT_UNAVAILABLE}"),
            "측정값 없는 영상이 목록에서 사라졌거나 숫자를 받았다:\n$prompt",
        )
    }

    /** 측정 불가 문구는 숫자가 아니어야 한다. 어떤 숫자든 모델은 측정값으로 읽는다. */
    @Test
    @DisplayName("측정 불가 문구에 숫자나 %가 들어가지 않는다")
    fun unavailableTextIsNotANumber() {
        assertFalse(
            Regex("[0-9]").containsMatchIn(ChannelAuditUseCase.ENGAGEMENT_UNAVAILABLE),
            "측정 불가 문구에 숫자가 있다: ${ChannelAuditUseCase.ENGAGEMENT_UNAVAILABLE}",
        )
        assertFalse(ChannelAuditUseCase.ENGAGEMENT_UNAVAILABLE.contains("%"))
    }

    /** 상위 영상이 하나도 없으면 기존대로 "데이터 없음" 이다. */
    @Test
    @DisplayName("상위 영상이 없으면 데이터 없음으로 남는다")
    fun noTopVideosKeepsTheEmptyMarker() {
        val prompt = renderedPrompt(crossPlatform = emptyList(), topVideos = emptyList())

        assertTrue(prompt.contains("데이터 없음"), "빈 목록 표기가 사라졌다:\n$prompt")
        assertFalse(prompt.contains("참여율"), "영상이 없는데 참여율 줄이 생겼다:\n$prompt")
    }

    // ── 프롬프트 오염 방지 ───────────────────────────────────────────────────

    @Test
    @DisplayName("프롬프트에 null/NaN/Infinity가 새지 않는다")
    fun promptHasNoFabricatedTokens() {
        val prompt = renderedPrompt(
            crossPlatform = listOf(row(1, "YOUTUBE", views = 0, likes = 0, comments = 0, shares = 0)),
            topVideos = listOf(video(1), video(2)),
        )

        listOf("null", "NaN", "Infinity").forEach {
            assertFalse(prompt.contains(it), "프롬프트에 '$it' 가 남았다:\n$prompt")
        }
    }

    // ── 채널 총 구독자 수 ────────────────────────────────────────────────────
    //
    // `ThreadsClient.kt:205` 와 `LinkedInClient.kt:264` 는 팔로워 수를 **묻지도 않고**
    // `subscriberCount = 0` 을 박아 넣는다. 그 채널만 연동한 크리에이터는 합계가 0 이 되어
    // "채널 총 구독자 수: 0" 이 진단 근거로 유료 프롬프트에 들어갔다.

    private fun channel(platform: Platform, subscribers: Long) = Channel(
        userId = userId,
        platform = platform,
        platformChannelId = "ch-${platform.name}",
        channelName = platform.name,
        subscriberCount = subscribers,
        accessToken = EncryptedToken("token"),
    )

    private fun promptWithChannels(vararg channels: Channel): String = renderedPrompt(
        crossPlatform = listOf(row(1, "YOUTUBE", views = 1000, likes = 50, comments = 30, shares = 5)),
        topVideos = listOf(video(1)),
        channels = channels.toList(),
    )

    /** **이 케이스가 "채널 총 구독자 수: 0" 을 지어내던 자리다.** */
    @Test
    @DisplayName("구독자 수를 조회하지 않는 채널만 있으면 0 을 보내지 않는다")
    fun unmeasuredSubscribersNeverReachThePrompt() {
        val prompt = promptWithChannels(
            channel(Platform.THREADS, 0),
            channel(Platform.LINKEDIN, 0),
        )

        assertFalse("채널 총 구독자 수: 0" in prompt, "재지 않은 구독자 수를 0 명으로 보냈다:\n$prompt")
        assertTrue(
            "채널 총 구독자 수: ${MetricChange.NOT_MEASURED_TEXT}" in prompt,
            "미측정을 알리지 않았다:\n$prompt",
        )
    }

    /** **조회하는 플랫폼의 0 은 관측이다.** */
    @Test
    @DisplayName("측정된 0 구독자는 숫자로 보존한다")
    fun measuredZeroSubscribersStaysANumber() {
        val prompt = promptWithChannels(channel(Platform.YOUTUBE, 0))

        assertTrue("채널 총 구독자 수: 0" in prompt, "실측 0 을 문장으로 감췄다:\n$prompt")
    }

    /** 재지 않는 채널이 섞여도 **잰 채널의 구독자 수는 그대로** 간다. */
    @Test
    @DisplayName("측정된 구독자 수는 그대로 보내고 미측정 채널을 더하지 않는다")
    fun measuredSubscribersReachThePrompt() {
        val prompt = promptWithChannels(
            channel(Platform.YOUTUBE, 8_000),
            channel(Platform.THREADS, 0),
        )

        assertTrue("채널 총 구독자 수: 8000" in prompt, "측정된 구독자 수가 빠졌다:\n$prompt")
    }

    /** 15 크레딧 과금 계약은 그대로다. */
    @Test
    @DisplayName("채널 오디트 요금은 15 크레딧 그대로다")
    fun creditCostUnchanged() {
        assertEquals(15, AiFeature.CHANNEL_AUDIT.creditCost)
    }
}
