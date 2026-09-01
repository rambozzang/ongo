package com.ongo.application.benchmark

import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.result.EngagementBenchmarkResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.CrossPlatformRaw
import com.ongo.common.enums.Platform
import com.ongo.domain.analytics.MetricChange
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.settings.UserSettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 참여율 벤치마크 프롬프트가 **미수집 지표를 숫자로 넣지 않는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * "- $platform: 조회수 $totalViews, 좋아요 $totalLikes, 댓글 $totalComments, 참여율 $engRate%"
 * ```
 *
 * 플랫폼별로 묶으면서도 가용성을 보지 않고 raw 를 더했다. `TumblrClient.kt:141` 의
 * `total_notes`(노트 총합)가 "조회수" 로, `PinterestClient.kt:158` 의 `SAVE`(저장) 수가
 * "좋아요" 로 **유료 LLM 프롬프트**에 들어갔다. 모델은 그것을 측정값으로 읽고 없는
 * 성과를 설명한다.
 *
 * ## 왜 프롬프트 문자열을 직접 잡나
 *
 * 이 경로의 산출물은 **프롬프트 그 자체**다. 응답 DTO 를 검사해서는 무엇이 모델에게
 * 전달됐는지 알 수 없다. 템플릿만 봐도 사용처가 바뀌면 놓친다.
 */
class BenchmarkPromptMeasurementTest {

    private val chatClientResolver = mockk<ChatClientResolver>()

    /**
     * **relaxed mock 으로 두면 안 된다.** `withCredits` 는 블록을 실행해 결과를 돌려주는
     * 함수인데, relaxed mock 은 블록을 부르지 않는다. 그러면 AI 도 호출되지 않아
     * 아래 검증이 **아무것도 검사하지 않으면서 통과**한다.
     */
    private val creditService = mockk<CreditService>()
    private val analyticsRepository = mockk<AnalyticsRepository>()

    /** 구독자 합계 계약을 검증하려면 연동 채널을 지정할 수 있어야 한다. */
    private val channelRepository = mockk<ChannelRepository>(relaxed = true)

    private val useCase = EngagementBenchmarkUseCase(
        chatClientResolver = chatClientResolver,
        creditService = creditService,
        rateLimiter = mockk<AiRateLimiter>(relaxed = true),
        channelRepository = channelRepository,
        analyticsRepository = analyticsRepository,
        userSettingsRepository = mockk<UserSettingsRepository>(relaxed = true),
    )

    private val userId = 7L

    private fun raw(
        platform: String,
        views: Long,
        likes: Long = 0,
        comments: Long = 0,
    ) = CrossPlatformRaw(
        videoId = 1L,
        videoTitle = "영상",
        platform = platform,
        videoUploadId = 101L,
        views = views,
        likes = likes,
        comments = comments,
        shares = 0,
        watchTimeSeconds = 0,
        revenueMicro = 0,
        impressions = 0,
        avgViewDurationSeconds = 0,
    )

    /** 유스케이스를 실제로 돌리고 AI 에게 간 user 프롬프트를 돌려준다. */
    private fun renderedPrompt(vararg rows: CrossPlatformRaw): String {
        every { analyticsRepository.findCrossPlatformMetrics(userId, 30) } returns rows.toList()
        every {
            creditService.withCredits(
                userId,
                AiFeature.ENGAGEMENT_BENCHMARK,
                any<() -> EngagementBenchmarkResult>(),
            )
        } answers { thirdArg<() -> EngagementBenchmarkResult>().invoke() }

        val userPrompt = slot<String>()
        val requestSpec = mockk<ChatClient.ChatClientRequestSpec>()
        val callSpec = mockk<ChatClient.CallResponseSpec>()
        val chatClient = mockk<ChatClient>()
        every { chatClientResolver.resolve(userId) } returns chatClient
        every { chatClient.prompt() } returns requestSpec
        every { requestSpec.system(any<String>()) } returns requestSpec
        every { requestSpec.user(capture(userPrompt)) } returns requestSpec
        every { requestSpec.call() } returns callSpec
        every { callSpec.entity(EngagementBenchmarkResult::class.java) } returns EngagementBenchmarkResult(
            myEngagementRate = 0.0,
            categoryAverage = 0.0,
            percentile = 0,
            platformBenchmarks = emptyList(),
            strengths = emptyList(),
            improvements = emptyList(),
        )

        useCase.execute(userId)
        return userPrompt.captured
    }

    // ── 이름이 다른 지표는 프롬프트에 숫자로 들어가지 않는다 ─────────────────

    /** **이 케이스가 노트 총합을 "조회수" 로 보내던 자리다.** */
    @Test
    @DisplayName("Tumblr 노트 총합을 조회수로 프롬프트에 넣지 않는다")
    fun tumblrNoteCountNeverReachesThePrompt() {
        val prompt = renderedPrompt(raw("TUMBLR", views = 900_000, likes = 60))

        assertFalse("900000" in prompt, "노트 총합이 조회수로 프롬프트에 들어갔다:\n$prompt")
        assertTrue(EngagementBenchmarkUseCase.NOT_COLLECTED in prompt, "미수집을 알리지 않았다:\n$prompt")
        // Tumblr 는 노트 목록에서 좋아요를 실제로 센다 — 그것은 남아야 한다.
        assertTrue("60" in prompt)
    }

    /** **저장(Save) 수를 "좋아요" 로 보내지 않는다.** */
    @Test
    @DisplayName("Pinterest 저장 수를 좋아요로 프롬프트에 넣지 않는다")
    fun pinterestSavesNeverReachThePrompt() {
        val prompt = renderedPrompt(raw("PINTEREST", views = 1_000, likes = 300))

        assertFalse("300" in prompt, "저장 수가 좋아요로 프롬프트에 들어갔다:\n$prompt")
        // 노출은 실제로 조회하므로 조회수는 남는다.
        assertTrue("1000" in prompt)
    }

    /**
     * 참여율은 분자와 분모가 **같은 관측**에서 나와야 한다. Pinterest 는 좋아요·댓글을
     * 하나도 주지 않으므로 비율이 성립하지 않는다 — `0.00%` 는 "참여가 없었다" 가 된다.
     */
    @Test
    @DisplayName("분자 지표가 없으면 참여율 0.00%를 만들지 않는다")
    fun noEngagementNumeratorProducesNoRate() {
        val prompt = renderedPrompt(raw("PINTEREST", views = 1_000, likes = 300))

        assertFalse("0.00" in prompt, "참여가 없었다는 관측을 지어냈다:\n$prompt")
    }

    // ── 수집 지표는 그대로 ───────────────────────────────────────────────────

    @Test
    @DisplayName("YouTube 지표는 숫자 그대로 프롬프트에 넣는다")
    fun youtubeMetricsReachThePrompt() {
        val prompt = renderedPrompt(raw("YOUTUBE", views = 1_000, likes = 50, comments = 30))

        assertTrue("1000" in prompt)
        assertTrue("50" in prompt)
        assertTrue("30" in prompt)
        // (50+30)/1000 = 8.00%
        assertTrue("8.00%" in prompt, "측정된 참여율이 빠졌다:\n$prompt")
        assertFalse(EngagementBenchmarkUseCase.NOT_COLLECTED in prompt)
    }

    /** **측정된 0 은 관측이다.** 조회는 있고 참여가 없었다는 뜻이다. */
    @Test
    @DisplayName("YouTube 의 실제 0 참여율은 0.00%로 넣는다")
    fun measuredZeroEngagementReachesThePrompt() {
        val prompt = renderedPrompt(raw("YOUTUBE", views = 1_000, likes = 0, comments = 0))

        assertTrue("0.00%" in prompt, "실측 0% 를 미측정으로 감췄다:\n$prompt")
    }

    // ── 총 구독자 수 ─────────────────────────────────────────────────────────
    //
    // `ThreadsClient.kt:205` 와 `LinkedInClient.kt:264` 는 팔로워 수를 **묻지도 않고**
    // `subscriberCount = 0` 을 박아 넣는다. 그 채널만 연동한 크리에이터는 합계가 0 이 되어
    // "총 구독자 수: 0" 이 벤치마크의 기준선으로 유료 프롬프트에 들어갔다.

    private fun channel(platform: Platform, subscribers: Long) = Channel(
        userId = userId,
        platform = platform,
        platformChannelId = "ch-${platform.name}",
        channelName = platform.name,
        subscriberCount = subscribers,
        accessToken = EncryptedToken("token"),
    )

    private fun promptWithChannels(vararg channels: Channel): String {
        every { channelRepository.findByUserId(userId) } returns channels.toList()
        return renderedPrompt(raw("YOUTUBE", views = 1_000, likes = 50, comments = 30))
    }

    /** **이 케이스가 "총 구독자 수: 0" 을 지어내던 자리다.** */
    @Test
    @DisplayName("구독자 수를 조회하지 않는 채널만 있으면 0 을 보내지 않는다")
    fun unmeasuredSubscribersNeverReachThePrompt() {
        val prompt = promptWithChannels(
            channel(Platform.THREADS, 0),
            channel(Platform.LINKEDIN, 0),
        )

        assertFalse("총 구독자 수: 0" in prompt, "재지 않은 구독자 수를 0 명으로 보냈다:\n$prompt")
        assertTrue(
            "총 구독자 수: ${MetricChange.NOT_MEASURED_TEXT}" in prompt,
            "미측정을 알리지 않았다:\n$prompt",
        )
    }

    /** **조회하는 플랫폼의 0 은 관측이다.** */
    @Test
    @DisplayName("측정된 0 구독자는 숫자로 보존한다")
    fun measuredZeroSubscribersStaysANumber() {
        val prompt = promptWithChannels(channel(Platform.YOUTUBE, 0))

        assertTrue("총 구독자 수: 0" in prompt, "실측 0 을 문장으로 감췄다:\n$prompt")
    }

    /** 재지 않는 채널이 섞여도 **잰 채널의 구독자 수는 그대로** 간다. */
    @Test
    @DisplayName("측정된 구독자 수는 그대로 보내고 미측정 채널을 더하지 않는다")
    fun measuredSubscribersReachThePrompt() {
        val prompt = promptWithChannels(
            channel(Platform.YOUTUBE, 8_000),
            channel(Platform.THREADS, 0),
        )

        assertTrue("총 구독자 수: 8000" in prompt, "측정된 구독자 수가 빠졌다:\n$prompt")
    }

    /** 미수집 문구는 **숫자가 아니라 문장**이어야 한다. */
    @Test
    @DisplayName("미수집 문구에 숫자가 들어가지 않는다")
    fun notCollectedTextIsASentence() {
        val text = EngagementBenchmarkUseCase.NOT_COLLECTED

        assertTrue(text.isNotBlank())
        assertFalse(Regex("[0-9]").containsMatchIn(text), "미수집 문구에 숫자가 있다: $text")
    }

    /**
     * 템플릿에서 `%` 를 뗐다. **단위를 값이 직접 들고 있어야** 미측정일 때
     * `측정 불가(...)%` 라는 문장이 만들어지지 않는다 —
     * `MetricChange.describePercent` 와 같은 이유다.
     */
    @Test
    @DisplayName("참여율 자리표시자 뒤에 단위가 붙어 있지 않다")
    fun engagementRatePlaceholderCarriesItsOwnUnit() {
        val template = com.ongo.application.ai.PromptTemplates.ENGAGEMENT_BENCHMARK_USER

        assertTrue("{engagementRate}" in template, "자리표시자가 사라졌다")
        assertFalse("{engagementRate}%" in template, "단위가 템플릿에 붙어 있어 문장과 충돌한다")
    }
}
