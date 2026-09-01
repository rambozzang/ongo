package com.ongo.application.ai

import com.ongo.application.ai.result.CompetitorInsightResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.enums.Platform
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.MetricChange
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.competitor.Competitor
import com.ongo.domain.competitor.CompetitorAnalyticsDaily
import com.ongo.domain.competitor.CompetitorRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import java.time.LocalDate
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 경쟁 인사이트 **유료 프롬프트**에 데이터 없는 자리의 `0` 이 들어가지 않는지 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * - 총 조회수: $myTotalViews      // 측정 행이 없으면 0
 * - 영상 수: $myVideoCount        // 측정 행이 없으면 0
 * ...30일 구독자 증감 ${subGrowth} // 수집 이력이 없으면 0
 * ```
 *
 * 모델은 그 `0` 을 관측값으로 읽는다. "조회수 0회, 영상 0편" 이면 "노출이 전혀 없다" 는
 * 진단을, 경쟁사 "증감 0" 이면 "정체된 경쟁사" 라는 우위를 지어낸다. 유료 호출이라
 * 그 대가까지 치른다.
 *
 * **실제로 측정된 0 은 보존한다** — 행이 있고 합이 0 인 것은 관측이다.
 */
class CompetitorInsightPromptMeasurementTest {

    private val chatClientResolver = mockk<ChatClientResolver>()

    /**
     * **relaxed mock 으로 두면 안 된다.** `withCredits` 는 블록을 실행해 결과를 돌려주는
     * 함수인데, relaxed mock 은 블록을 부르지 않는다. 그러면 AI 도 호출되지 않아
     * 아래 검증이 아무것도 검사하지 않으면서 통과한다.
     */
    private val creditService = mockk<CreditService>()
    private val competitorRepository = mockk<CompetitorRepository>()
    private val channelRepository = mockk<ChannelRepository>()
    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>()

    private val useCase = CompetitorInsightUseCase(
        chatClientResolver = chatClientResolver,
        creditService = creditService,
        rateLimiter = mockk<AiRateLimiter>(relaxed = true),
        competitorRepository = competitorRepository,
        channelRepository = channelRepository,
        analyticsRepository = analyticsRepository,
        videoUploadRepository = videoUploadRepository,
    )

    private val userId = 7L

    private fun row(views: Int) =
        AnalyticsDaily(videoUploadId = 11L, date = LocalDate.now().minusDays(2), views = views)

    private fun snapshot(daysAgo: Long, subscribers: Long) = CompetitorAnalyticsDaily(
        competitorId = 5L,
        date = LocalDate.now().minusDays(daysAgo),
        subscriberCount = subscribers,
    )

    /** 유스케이스를 실제로 돌리고 AI 에게 간 user 프롬프트를 돌려준다. */
    private fun renderedPrompt(
        uploadPlatform: Platform?,
        myRows: List<AnalyticsDaily>,
        competitorHistory: List<CompetitorAnalyticsDaily> = emptyList(),
        channels: List<Channel> = emptyList(),
    ): String {
        every { channelRepository.findByUserId(userId) } returns channels
        val uploads: List<VideoUpload> = uploadPlatform
            ?.let { listOf(VideoUpload(id = 11L, videoId = 1L, platform = it, channelId = 1L)) }
            ?: emptyList()
        every { videoUploadRepository.findByUserId(userId) } returns uploads
        every { analyticsRepository.findAllByUserId(userId) } returns myRows
        every { competitorRepository.findByUserId(userId) } returns listOf(
            Competitor(
                id = 5L,
                userId = userId,
                platform = "YOUTUBE",
                platformChannelId = "rival",
                channelName = "경쟁 채널",
                subscriberCount = 10_000,
                // 영상 수·총 조회수도 측정된 값을 준다. 비워 두면 `null` 이라 프롬프트의
                // 그 칸들이 정당하게 "측정 불가" 가 되고, 조회수와 무관한 이유로 아래
                // 단언이 걸린다.
                videoCount = 12,
                totalViews = 1_000,
                avgViews = 83,
            ),
        )
        every {
            competitorRepository.findAnalyticsByCompetitorIdAndDateRange(5L, any(), any())
        } returns competitorHistory

        every {
            creditService.withCredits(
                userId,
                AiFeature.COMPETITOR_INSIGHT,
                any<() -> CompetitorInsightResult>(),
            )
        } answers { thirdArg<() -> CompetitorInsightResult>().invoke() }

        val userPrompt = slot<String>()
        val requestSpec = mockk<ChatClient.ChatClientRequestSpec>()
        val callSpec = mockk<ChatClient.CallResponseSpec>()
        val chatClient = mockk<ChatClient>()
        every { chatClientResolver.resolve(userId) } returns chatClient
        every { chatClient.prompt() } returns requestSpec
        every { requestSpec.system(any<String>()) } returns requestSpec
        every { requestSpec.user(capture(userPrompt)) } returns requestSpec
        every { requestSpec.call() } returns callSpec
        every { callSpec.entity(CompetitorInsightResult::class.java) } returns CompetitorInsightResult(
            summary = "요약",
            strengths = emptyList(),
            weaknesses = emptyList(),
            opportunities = emptyList(),
            recommendations = emptyList(),
        )

        useCase.execute(userId)
        return userPrompt.captured
    }

    // ── 내 채널 조회수·영상 수 ───────────────────────────────────────────────

    /** **이 케이스가 "조회수 0회, 영상 0편" 을 보내던 자리다.** */
    @Test
    @DisplayName("조회수 측정 행이 없으면 0 대신 문장을 넣는다")
    fun noViewRowsSendsASentence() {
        val prompt = renderedPrompt(uploadPlatform = Platform.TUMBLR, myRows = listOf(row(900_000)))

        assertTrue(
            CompetitorInsightUseCase.NOT_COLLECTED in prompt,
            "미수집을 알리지 않았다:\n$prompt",
        )
        assertFalse("총 조회수: 0" in prompt, "0 을 관측처럼 보냈다:\n$prompt")
        assertFalse("영상 수: 0" in prompt, "0 편을 관측처럼 보냈다:\n$prompt")
        // 노트 총합 자체도 새어 나가면 안 된다.
        assertFalse("900000" in prompt, "노트 총합이 조회수로 들어갔다:\n$prompt")
    }

    /** **행이 있고 합이 0 이면 그 0 은 실측이다.** */
    @Test
    @DisplayName("측정된 0 조회수는 숫자로 보존한다")
    fun measuredZeroViewsStaysANumber() {
        val prompt = renderedPrompt(uploadPlatform = Platform.YOUTUBE, myRows = listOf(row(0)))

        assertTrue("총 조회수: 0" in prompt, "실측 0 을 문장으로 감췄다:\n$prompt")
        assertTrue("영상 수: 1" in prompt)
    }

    /**
     * 모든 자리가 측정됐을 때 프롬프트에 미수집 문구가 **하나도** 남지 않는지 본다.
     *
     * 구독자 수도 측정된 채널을 준다. 채널을 비워 두면 구독자 줄이 정당하게 "측정 불가"가
     * 되어 — `NOT_COLLECTED` 와 같은 문자열이다 — 아래 단언이 조회수와 무관한 이유로
     * 걸린다.
     */
    @Test
    @DisplayName("측정된 조회수는 그대로 보낸다")
    fun measuredViewsReachThePrompt() {
        val prompt = renderedPrompt(
            uploadPlatform = Platform.YOUTUBE,
            myRows = listOf(row(1_500)),
            channels = listOf(channel(Platform.YOUTUBE, 8_000)),
        )

        assertTrue("총 조회수: 1500" in prompt)
        assertFalse(CompetitorInsightUseCase.NOT_COLLECTED in prompt)
    }

    // ── 경쟁사 30일 구독 증감 ────────────────────────────────────────────────

    /** **이 케이스가 "증감 0" 으로 정체된 경쟁사를 지어내던 자리다.** */
    @Test
    @DisplayName("경쟁사 수집 이력이 없으면 증감 0을 보내지 않는다")
    fun noCompetitorHistorySendsASentence() {
        val prompt = renderedPrompt(
            uploadPlatform = Platform.YOUTUBE,
            myRows = listOf(row(1_000)),
            competitorHistory = emptyList(),
        )

        assertTrue(
            CompetitorInsightUseCase.NO_COMPETITOR_HISTORY in prompt,
            "미수집을 알리지 않았다:\n$prompt",
        )
        assertFalse("구독자 증감 0" in prompt, "0 을 관측처럼 보냈다:\n$prompt")
    }

    /** 관측이 하나뿐이면 시작과 끝이 같은 행이라 변화를 잰 적이 없다. */
    @Test
    @DisplayName("경쟁사 관측이 하나뿐이면 증감을 만들지 않는다")
    fun singleCompetitorObservationSendsASentence() {
        val prompt = renderedPrompt(
            uploadPlatform = Platform.YOUTUBE,
            myRows = listOf(row(1_000)),
            competitorHistory = listOf(snapshot(daysAgo = 10, subscribers = 10_000)),
        )

        assertTrue(CompetitorInsightUseCase.NO_COMPETITOR_HISTORY in prompt, "한 시점만 보고 증감을 말했다")
    }

    /** **두 시점이 관측됐고 값이 같으면 그 0 은 실측이다.** */
    @Test
    @DisplayName("관측된 두 시점이 같으면 증감 0을 보존한다")
    fun measuredZeroCompetitorGrowthIsPreserved() {
        val prompt = renderedPrompt(
            uploadPlatform = Platform.YOUTUBE,
            myRows = listOf(row(1_000)),
            competitorHistory = listOf(
                snapshot(daysAgo = 20, subscribers = 10_000),
                snapshot(daysAgo = 1, subscribers = 10_000),
            ),
        )

        assertTrue("구독자 증감 0" in prompt, "실측 0 을 문장으로 감췄다:\n$prompt")
        assertFalse(CompetitorInsightUseCase.NO_COMPETITOR_HISTORY in prompt)
    }

    @Test
    @DisplayName("관측된 두 시점이 다르면 증감을 그대로 보낸다")
    fun measuredCompetitorGrowthReachesThePrompt() {
        val prompt = renderedPrompt(
            uploadPlatform = Platform.YOUTUBE,
            myRows = listOf(row(1_000)),
            competitorHistory = listOf(
                snapshot(daysAgo = 20, subscribers = 10_000),
                snapshot(daysAgo = 1, subscribers = 11_500),
            ),
        )

        assertTrue("구독자 증감 1500" in prompt, "측정된 증감이 빠졌다:\n$prompt")
    }

    // ── 내 채널 구독자 수 ────────────────────────────────────────────────────
    //
    // `ThreadsClient.kt:205` 와 `LinkedInClient.kt:264` 는 팔로워 수를 **묻지도 않고**
    // `subscriberCount = 0` 을 박아 넣는다. 그 채널만 연동한 크리에이터의 합계는 항상 0 이
    // 되어, 경쟁사 구독자 10,000 과 나란히 놓인 "총 구독자: 0" 이 유료 프롬프트로 갔다.

    private fun channel(platform: Platform, subscribers: Long) = Channel(
        userId = userId,
        platform = platform,
        platformChannelId = "ch-${platform.name}",
        channelName = platform.name,
        subscriberCount = subscribers,
        accessToken = EncryptedToken("token"),
    )

    /** **이 케이스가 "총 구독자: 0" 으로 없는 열세를 지어내던 자리다.** */
    @Test
    @DisplayName("구독자 수를 조회하지 않는 채널만 있으면 0 을 보내지 않는다")
    fun unmeasuredSubscribersNeverReachThePrompt() {
        val prompt = renderedPrompt(
            uploadPlatform = Platform.YOUTUBE,
            myRows = listOf(row(1_500)),
            channels = listOf(channel(Platform.THREADS, 0), channel(Platform.LINKEDIN, 0)),
        )

        assertFalse("총 구독자: 0" in prompt, "재지 않은 구독자 수를 0 명으로 보냈다:\n$prompt")
        assertTrue(
            "총 구독자: ${MetricChange.NOT_MEASURED_TEXT}" in prompt,
            "미측정을 알리지 않았다:\n$prompt",
        )
    }

    /** **조회하는 플랫폼의 0 은 관측이다.** 갓 만든 채널의 구독자 0 명. */
    @Test
    @DisplayName("측정된 0 구독자는 숫자로 보존한다")
    fun measuredZeroSubscribersStaysANumber() {
        val prompt = renderedPrompt(
            uploadPlatform = Platform.YOUTUBE,
            myRows = listOf(row(1_500)),
            channels = listOf(channel(Platform.YOUTUBE, 0)),
        )

        assertTrue("총 구독자: 0" in prompt, "실측 0 을 문장으로 감췄다:\n$prompt")
    }

    /** 재지 않는 채널이 섞여도 **잰 채널의 구독자 수는 그대로** 간다. */
    @Test
    @DisplayName("측정된 구독자 수는 그대로 보내고 미측정 채널을 더하지 않는다")
    fun measuredSubscribersReachThePrompt() {
        val prompt = renderedPrompt(
            uploadPlatform = Platform.YOUTUBE,
            myRows = listOf(row(1_500)),
            channels = listOf(channel(Platform.YOUTUBE, 8_000), channel(Platform.THREADS, 0)),
        )

        assertTrue("총 구독자: 8000" in prompt, "측정된 구독자 수가 빠졌다:\n$prompt")
    }

    /** 미수집 문구는 **숫자가 아니라 문장**이어야 한다. */
    @Test
    @DisplayName("미수집 문구에 숫자가 들어가지 않는다")
    fun notCollectedTextsAreSentences() {
        listOf(
            CompetitorInsightUseCase.NOT_COLLECTED,
            CompetitorInsightUseCase.NO_COMPETITOR_HISTORY,
        ).forEach { text ->
            assertTrue(text.isNotBlank())
            assertFalse(Regex("[0-9]").containsMatchIn(text), "미수집 문구에 숫자가 있다: $text")
        }
    }
}
