package com.ongo.application.ai

import com.ongo.application.ai.result.PerformanceReportResult
import com.ongo.application.ai.result.StrategyCoachResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.DashboardKpi
import com.ongo.domain.analytics.MetricChange
import com.ongo.domain.competitor.CompetitorRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 성과 리포트·전략 코치 **유료 프롬프트**에 KPI 의 `null` 이 새지 않는지 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * .replace("{totalViews}", kpi.totalViews.toString())   // Long? → "null"
 * ```
 *
 * `totalViews`·`totalLikes`·`totalComments` 는 그 지표를 수집하는 플랫폼의 행이 없으면
 * `null` 이다(`TumblrClient.kt:141` 은 노트 총합을 `views` 에, `PinterestClient.kt:158` 은
 * 저장 수를 `likes` 에 넣어 집계에서 빠진다). `Long?.toString()` 은 **문자열 `"null"`** 을
 * 만들고, 모델은 그것을 수치로 읽어 없는 성과를 설명한다. 유료 호출이라 대가까지 치른다.
 *
 * 증감률 쪽은 이미 [MetricChange.describePercent] 를 쓰고 있었다
 * ([PerformancePromptChangeTest] 가 템플릿 계약을 지킨다). **절대 수치만 남아 있었다.**
 *
 * 여기서는 템플릿이 아니라 **실제 유스케이스 실행**으로 만들어진 프롬프트를 붙잡는다.
 */
class KpiPromptMeasurementTest {

    private val chatClientResolver = mockk<ChatClientResolver>()

    /**
     * **relaxed mock 으로 두면 안 된다.** `withCredits` 는 블록을 실행해 결과를 돌려주는
     * 함수인데, relaxed mock 은 블록을 부르지 않는다. 그러면 AI 도 호출되지 않아
     * 아래 검증이 아무것도 검사하지 않으면서 통과한다.
     */
    private val creditService = mockk<CreditService>()
    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val videoRepository = mockk<VideoRepository>()
    private val competitorRepository = mockk<CompetitorRepository>()

    private val reportUseCase = GenerateReportUseCase(
        chatClientResolver = chatClientResolver,
        creditService = creditService,
        rateLimiter = mockk<AiRateLimiter>(relaxed = true),
        analyticsRepository = analyticsRepository,
    )

    private val coachUseCase = StrategyCoachUseCase(
        chatClientResolver = chatClientResolver,
        creditService = creditService,
        rateLimiter = mockk<AiRateLimiter>(relaxed = true),
        analyticsRepository = analyticsRepository,
        videoRepository = videoRepository,
        competitorRepository = competitorRepository,
    )

    private val userId = 7L
    private val days = 30

    private fun kpi(
        totalViews: Long? = 50_000,
        totalLikes: Long? = 1_200,
        totalComments: Long? = 34,
    ) = DashboardKpi(
        totalViews = totalViews,
        totalViewsChange = 5.0,
        totalSubscribers = 100,
        totalSubscribersChange = 12,
        totalLikes = totalLikes,
        totalLikesChange = 5.0,
        creditBalance = 500,
        creditTotal = 1000,
        totalComments = totalComments,
    )

    /** AI 호출 체인을 붙잡는다. `entity` 응답 타입만 호출부마다 다르다. */
    private fun <T : Any> captureChain(result: T, type: Class<T>): CapturingSlot<String> {
        val userPrompt = slot<String>()
        val requestSpec = mockk<ChatClient.ChatClientRequestSpec>()
        val callSpec = mockk<ChatClient.CallResponseSpec>()
        val chatClient = mockk<ChatClient>()
        every { chatClientResolver.resolve(userId) } returns chatClient
        every { chatClient.prompt() } returns requestSpec
        every { requestSpec.system(any<String>()) } returns requestSpec
        every { requestSpec.user(capture(userPrompt)) } returns requestSpec
        every { requestSpec.call() } returns callSpec
        every { callSpec.entity(type) } returns result
        return userPrompt
    }

    // ── 성과 리포트 ──────────────────────────────────────────────────────────

    private fun reportPrompt(kpi: DashboardKpi): String {
        every { analyticsRepository.getDashboardKpi(userId, days) } returns kpi
        every { analyticsRepository.getTopVideos(userId, days, 5) } returns listOf(
            Video(id = 1, userId = userId, title = "테스트 영상"),
        )
        every {
            creditService.withCredits(userId, AiFeature.PERFORMANCE_REPORT, any<() -> PerformanceReportResult>())
        } answers { thirdArg<() -> PerformanceReportResult>().invoke() }

        val captured = captureChain(
            PerformanceReportResult(
                reportMarkdown = "# 리포트",
                highlights = emptyList(),
                improvements = emptyList(),
                nextWeekSuggestions = emptyList(),
            ),
            PerformanceReportResult::class.java,
        )

        reportUseCase.execute(userId, days)

        assertTrue(captured.isCaptured, "AI user 프롬프트를 붙잡지 못했다")
        return captured.captured
    }

    /** **이 케이스가 "총 조회수: null" 을 유료 프롬프트에 보내던 자리다.** */
    @Test
    @DisplayName("리포트: 조회수가 미측정이면 null 대신 문장을 보낸다")
    fun reportUnmeasuredViews() {
        val prompt = reportPrompt(kpi(totalViews = null))

        assertTrue(
            "총 조회수: ${MetricChange.NOT_MEASURED_TEXT}" in prompt,
            "미측정을 알리지 않았다:\n$prompt",
        )
        assertNoFabricatedNumbers(prompt)
    }

    @Test
    @DisplayName("리포트: 좋아요가 미측정이면 null 대신 문장을 보낸다")
    fun reportUnmeasuredLikes() {
        val prompt = reportPrompt(kpi(totalLikes = null))

        assertTrue("총 좋아요: ${MetricChange.NOT_MEASURED_TEXT}" in prompt, "미측정을 알리지 않았다:\n$prompt")
        assertNoFabricatedNumbers(prompt)
    }

    @Test
    @DisplayName("리포트: 댓글이 미측정이면 null 대신 문장을 보낸다")
    fun reportUnmeasuredComments() {
        val prompt = reportPrompt(kpi(totalComments = null))

        assertTrue("총 댓글: ${MetricChange.NOT_MEASURED_TEXT}" in prompt, "미측정을 알리지 않았다:\n$prompt")
        assertNoFabricatedNumbers(prompt)
    }

    /** **측정된 0 은 관측이다.** */
    @Test
    @DisplayName("리포트: 측정된 0 은 숫자 0 으로 보낸다")
    fun reportMeasuredZero() {
        val prompt = reportPrompt(kpi(totalViews = 0, totalLikes = 0, totalComments = 0))

        assertTrue("총 조회수: 0" in prompt, "실측 0 을 문장으로 감췄다:\n$prompt")
        assertTrue("총 좋아요: 0" in prompt, "실측 0 을 문장으로 감췄다:\n$prompt")
        assertTrue("총 댓글: 0" in prompt, "실측 0 을 문장으로 감췄다:\n$prompt")
        assertFalse(MetricChange.NOT_MEASURED_TEXT in prompt, "실측 0 을 미측정으로 바꿨다:\n$prompt")
    }

    @Test
    @DisplayName("리포트: 측정된 수치는 그대로 보낸다")
    fun reportMeasuredValues() {
        val prompt = reportPrompt(kpi())

        assertTrue("총 조회수: 50000" in prompt, "측정값이 훼손됐다:\n$prompt")
        assertTrue("총 좋아요: 1200" in prompt)
        assertTrue("총 댓글: 34" in prompt)
    }

    /** 미측정 문구에 단위가 붙으면 문장이 깨진다. 치환 누락도 함께 본다. */
    @Test
    @DisplayName("리포트: 미측정 문구에 단위가 붙거나 자리표시자가 남지 않는다")
    fun reportPromptStaysWellFormed() {
        val prompt = reportPrompt(kpi(totalViews = null, totalLikes = null, totalComments = null))

        assertFalse("${MetricChange.NOT_MEASURED_TEXT}%" in prompt, "미측정 문구에 % 가 붙었다:\n$prompt")
        assertFalse(Regex("\\{\\w+}").containsMatchIn(prompt), "치환되지 않은 자리표시자가 남았다:\n$prompt")
    }

    // ── 전략 코치 ────────────────────────────────────────────────────────────

    private fun coachPrompt(kpi: DashboardKpi): String {
        every { analyticsRepository.getDashboardKpi(userId, 30) } returns kpi
        every { videoRepository.findByUserId(userId, any(), any()) } returns listOf(
            Video(id = 1, userId = userId, title = "테스트 영상"),
        )
        every { competitorRepository.findByUserId(userId) } returns emptyList()
        every {
            creditService.withCredits(userId, AiFeature.STRATEGY_COACH, any<() -> StrategyCoachResult>())
        } answers { thirdArg<() -> StrategyCoachResult>().invoke() }

        val captured = captureChain(
            StrategyCoachResult(
                contentRecommendations = emptyList(),
                platformStrategy = emptyList(),
                timingAdvice = emptyList(),
                overallStrategy = "전략",
            ),
            StrategyCoachResult::class.java,
        )

        coachUseCase.execute(userId)

        assertTrue(captured.isCaptured, "AI user 프롬프트를 붙잡지 못했다")
        return captured.captured
    }

    /** **이 케이스가 "총 조회수: null" 로 전략을 세우게 하던 자리다.** */
    @Test
    @DisplayName("코치: 조회수가 미측정이면 null 대신 문장을 보낸다")
    fun coachUnmeasuredViews() {
        val prompt = coachPrompt(kpi(totalViews = null))

        assertTrue(
            "총 조회수: ${MetricChange.NOT_MEASURED_TEXT}" in prompt,
            "미측정을 알리지 않았다:\n$prompt",
        )
        assertNoFabricatedNumbers(prompt)
    }

    @Test
    @DisplayName("코치: 좋아요가 미측정이면 null 대신 문장을 보낸다")
    fun coachUnmeasuredLikes() {
        val prompt = coachPrompt(kpi(totalLikes = null))

        assertTrue("총 좋아요: ${MetricChange.NOT_MEASURED_TEXT}" in prompt, "미측정을 알리지 않았다:\n$prompt")
        assertNoFabricatedNumbers(prompt)
    }

    @Test
    @DisplayName("코치: 측정된 0 은 숫자 0 으로 보낸다")
    fun coachMeasuredZero() {
        val prompt = coachPrompt(kpi(totalViews = 0, totalLikes = 0))

        assertTrue("총 조회수: 0" in prompt, "실측 0 을 문장으로 감췄다:\n$prompt")
        assertTrue("총 좋아요: 0" in prompt, "실측 0 을 문장으로 감췄다:\n$prompt")
        assertFalse(MetricChange.NOT_MEASURED_TEXT in prompt, "실측 0 을 미측정으로 바꿨다:\n$prompt")
    }

    @Test
    @DisplayName("코치: 측정된 수치는 그대로 보낸다")
    fun coachMeasuredValues() {
        val prompt = coachPrompt(kpi())

        assertTrue("총 조회수: 50000" in prompt, "측정값이 훼손됐다:\n$prompt")
        assertTrue("총 좋아요: 1200" in prompt)
    }

    @Test
    @DisplayName("코치: 미측정 문구에 단위가 붙거나 자리표시자가 남지 않는다")
    fun coachPromptStaysWellFormed() {
        val prompt = coachPrompt(kpi(totalViews = null, totalLikes = null))

        assertFalse("${MetricChange.NOT_MEASURED_TEXT}%" in prompt, "미측정 문구에 % 가 붙었다:\n$prompt")
        assertFalse(Regex("\\{\\w+}").containsMatchIn(prompt), "치환되지 않은 자리표시자가 남았다:\n$prompt")
    }

    // ── 공통 ─────────────────────────────────────────────────────────────────

    /** 프롬프트에 절대 새면 안 되는 토큰. 하나라도 나오면 모델이 수치로 읽는다. */
    private fun assertNoFabricatedNumbers(prompt: String) {
        listOf("null", "NaN", "Infinity", "-Infinity").forEach {
            assertFalse(it in prompt, "프롬프트에 '$it' 가 남았다:\n$prompt")
        }
    }

    /** 미수집 문구는 숫자가 아니라 **문장**이어야 한다. */
    @Test
    @DisplayName("미측정 문구에 숫자가 들어가지 않는다")
    fun notMeasuredTextIsASentence() {
        val text = MetricChange.NOT_MEASURED_TEXT

        assertTrue(text.isNotBlank())
        assertFalse(Regex("[0-9]").containsMatchIn(text), "미측정 문구에 숫자가 있다: $text")
    }
}
