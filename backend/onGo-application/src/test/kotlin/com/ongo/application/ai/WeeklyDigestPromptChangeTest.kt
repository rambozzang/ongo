package com.ongo.application.ai

import com.ongo.application.ai.result.WeeklyDigestResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.ai.WeeklyDigest
import com.ongo.domain.ai.WeeklyDigestRepository
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.DashboardKpi
import com.ongo.domain.analytics.MetricChange
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.video.Video
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 주간 다이제스트 프롬프트에 **측정하지 않은 수치가 들어가지 않는지** 고정한다.
 *
 * 예전 코드는 두 가지를 지어냈다.
 *
 * 1. 분모가 0 이면 `0.0` 으로 채웠다. 비교할 이전 주 데이터가 없다는 사실이
 *    **"변화 없음"이라는 측정 결과**로 둔갑해 AI 에게 전달됐다. 이번 주 첫 조회수를
 *    낸 크리에이터가 "정체 중"이라는 다이제스트를 받았다.
 * 2. 이전 기간을 `getDashboardKpi(userId, 14)` 의 **총합**으로 잡았다. 14일 총합은
 *    최근 7일을 포함하므로 `(7일 - 14일) / 14일` 은 조회수가 음수일 수 없는 이상
 *    **항상 0 이하**다. 조회수가 두 배로 뛴 주에도 AI 는 하락을 통보받았다.
 *
 * 이제 7일 KPI 가 이미 들고 있는 [MetricChange] 값을 그대로 쓴다. 그 값의 분모는
 * 겹치지 않는 직전 7일이고, 0 이면 `null`(비교 불가)이다.
 *
 * 여기서는 스텁이 아니라 **실제 [WeeklyDigestUseCase.generateDigest] 호출**로 만들어진
 * user 프롬프트를 붙잡아 검사한다. 템플릿만 보면 사용처가 바뀌었을 때 놓친다.
 */
class WeeklyDigestPromptChangeTest {

    private val chatClientResolver = mockk<ChatClientResolver>()

    /**
     * **relaxed mock 으로 두면 안 된다.** `withCredits` 는 블록을 실행해 그 결과를
     * 돌려주는 함수인데, relaxed mock 은 블록을 부르지 않고 더미를 돌려준다. 그러면
     * AI 도 호출되지 않아 아래 프롬프트 검증이 **아무것도 검사하지 않으면서 통과**한다.
     * 실제 계약대로 블록을 돌린다. 차감 자체의 계약은 [WeeklyDigestCreditTest] 소관이다.
     */
    private val creditService = mockk<CreditService>()
    private val rateLimiter = mockk<AiRateLimiter>(relaxed = true)
    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val weeklyDigestRepository = mockk<WeeklyDigestRepository>()
    private val subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true)
    private val userWriteGuard = mockk<UserWriteGuard>(relaxed = true)

    private val useCase = WeeklyDigestUseCase(
        chatClientResolver = chatClientResolver,
        creditService = creditService,
        rateLimiter = rateLimiter,
        analyticsRepository = analyticsRepository,
        weeklyDigestRepository = weeklyDigestRepository,
        subscriptionRepository = subscriptionRepository,
        userWriteGuard = userWriteGuard,
    )

    private val userId = 7L
    private val weekStart = LocalDate.of(2026, 8, 17)
    private val weekEnd = LocalDate.of(2026, 8, 23)

    private fun kpi(
        totalViews: Long?,
        totalViewsChange: Double?,
        totalLikes: Long?,
        totalLikesChange: Double?,
        totalComments: Long? = 34,
    ) = DashboardKpi(
        totalViews = totalViews,
        totalViewsChange = totalViewsChange,
        totalSubscribers = 100,
        totalSubscribersChange = 12,
        totalLikes = totalLikes,
        totalLikesChange = totalLikesChange,
        creditBalance = 500,
        creditTotal = 1000,
        totalComments = totalComments,
    )

    /**
     * 유스케이스를 실제로 돌리고 AI 에게 간 user 프롬프트를 돌려준다.
     *
     * `days` 를 `any()` 로 스텁하지 않는다. 7일 KPI 만 스텁해 두면, 예전처럼 14일 KPI 를
     * 다시 조회하는 코드가 되살아났을 때 **mockk 가 미설정 호출로 실패**한다.
     */
    private fun renderedPrompt(kpi: DashboardKpi): String {
        every { creditService.withCredits(userId, AiFeature.WEEKLY_DIGEST, any<() -> WeeklyDigest>()) } answers {
            thirdArg<() -> WeeklyDigest>().invoke()
        }
        every { analyticsRepository.getDashboardKpi(userId, 7) } returns kpi
        every { analyticsRepository.getTopVideos(userId, 7, 3) } returns listOf(
            Video(id = 1, userId = userId, title = "테스트 영상"),
        )

        val userPrompt = slot<String>()
        val requestSpec = mockk<ChatClient.ChatClientRequestSpec>()
        val callSpec = mockk<ChatClient.CallResponseSpec>()
        val chatClient = mockk<ChatClient>()
        every { chatClientResolver.resolve(userId) } returns chatClient
        every { chatClient.prompt() } returns requestSpec
        every { requestSpec.system(any<String>()) } returns requestSpec
        every { requestSpec.user(capture(userPrompt)) } returns requestSpec
        every { requestSpec.call() } returns callSpec
        every { callSpec.entity(WeeklyDigestResult::class.java) } returns WeeklyDigestResult(
            summary = "요약",
            topVideos = listOf(WeeklyDigestResult.TopVideoInsight("테스트 영상", 100, "인사이트")),
            anomalies = listOf("특이사항 없음"),
            actionItems = listOf("다음 주 업로드 2회"),
        )
        every { weeklyDigestRepository.save(any()) } answers { firstArg<WeeklyDigest>() }

        useCase.generateDigest(userId, weekStart, weekEnd)

        assertTrue(userPrompt.isCaptured, "AI user 프롬프트를 붙잡지 못했다")
        return userPrompt.captured
    }

    /** 프롬프트에 절대 새면 안 되는 토큰. 하나라도 나오면 모델이 수치로 읽는다. */
    private fun assertNoFabricatedNumbers(prompt: String) {
        listOf("null", "NaN", "Infinity", "-Infinity").forEach {
            assertFalse(prompt.contains(it), "프롬프트에 '$it' 가 남았다:\n$prompt")
        }
    }

    // ── 비교 불가 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("이전 주 조회수가 없으면 0% 변화가 아니라 비교 불가로 알린다")
    fun zeroViewsBaselineIsReportedAsUnavailable() {
        val prompt = renderedPrompt(
            kpi(totalViews = 50_000, totalViewsChange = null, totalLikes = 1_200, totalLikesChange = 30.0),
        )

        assertTrue(
            prompt.contains("총 조회수: 50000 (전주 대비 변화율: ${MetricChange.UNAVAILABLE_TEXT})"),
            "조회수 비교 불가가 문장으로 전달되지 않았다:\n$prompt",
        )
        // 예전 값이 되살아나면 여기서 걸린다.
        assertFalse(prompt.contains("변화율: 0.0"), "비교 불가를 0.0 으로 채웠다:\n$prompt")
        assertFalse(prompt.contains("변화율: 100.0"), "비교 불가를 100.0 으로 채웠다:\n$prompt")
        assertNoFabricatedNumbers(prompt)
    }

    @Test
    @DisplayName("이전 주 좋아요가 없으면 0% 변화가 아니라 비교 불가로 알린다")
    fun zeroLikesBaselineIsReportedAsUnavailable() {
        val prompt = renderedPrompt(
            kpi(totalViews = 50_000, totalViewsChange = 12.0, totalLikes = 1_200, totalLikesChange = null),
        )

        assertTrue(
            prompt.contains("총 좋아요: 1200 (전주 대비 변화율: ${MetricChange.UNAVAILABLE_TEXT})"),
            "좋아요 비교 불가가 문장으로 전달되지 않았다:\n$prompt",
        )
        assertFalse(prompt.contains("변화율: 0.0"), "비교 불가를 0.0 으로 채웠다:\n$prompt")
        assertNoFabricatedNumbers(prompt)
    }

    @Test
    @DisplayName("둘 다 비교 불가여도 프롬프트에 null/NaN 이 새지 않는다")
    fun bothUnavailableStayTextual() {
        val prompt = renderedPrompt(
            kpi(totalViews = 0, totalViewsChange = null, totalLikes = 0, totalLikesChange = null),
        )

        assertEquals(
            2,
            Regex(Regex.escape(MetricChange.UNAVAILABLE_TEXT)).findAll(prompt).count(),
            "비교 불가 문구가 두 지표 모두에 들어가지 않았다:\n$prompt",
        )
        assertNoFabricatedNumbers(prompt)
    }

    // ── 측정된 값 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("측정된 증감률은 단위와 부호를 유지한다")
    fun measuredChangesKeepSignAndUnit() {
        val prompt = renderedPrompt(
            kpi(totalViews = 50_000, totalViewsChange = 42.35, totalLikes = 1_200, totalLikesChange = -8.0),
        )

        assertTrue(prompt.contains("전주 대비 변화율: 42.4%"), "양수 증감률이 훼손됐다:\n$prompt")
        assertTrue(prompt.contains("전주 대비 변화율: -8.0%"), "음수 증감률이 훼손됐다:\n$prompt")
        assertFalse(prompt.contains(MetricChange.UNAVAILABLE_TEXT), "측정값을 비교 불가로 감췄다:\n$prompt")
        assertNoFabricatedNumbers(prompt)
    }

    /**
     * **진짜 0% 는 측정된 사실이다.** 비교 불가와 섞으면 "지난주와 같았다"는 실제 관찰을
     * 잃는다. 반대 방향 회귀(과도한 null 처리)를 여기서 막는다.
     */
    @Test
    @DisplayName("실제로 변화가 없던 주는 0.0% 로 그대로 전달한다")
    fun genuineZeroPercentIsPreserved() {
        val prompt = renderedPrompt(
            kpi(totalViews = 50_000, totalViewsChange = 0.0, totalLikes = 1_200, totalLikesChange = 0.0),
        )

        assertTrue(prompt.contains("총 조회수: 50000 (전주 대비 변화율: 0.0%)"), "측정된 0% 가 사라졌다:\n$prompt")
        assertFalse(prompt.contains(MetricChange.UNAVAILABLE_TEXT), "측정된 0% 를 비교 불가로 바꿨다:\n$prompt")
        assertNoFabricatedNumbers(prompt)
    }

    // ── 템플릿 계약 ──────────────────────────────────────────────────────────

    /**
     * 단위는 [MetricChange.describePercent] 가 값과 함께 만든다. 템플릿이 `%` 를 밖에
     * 붙이면 비교 불가일 때 `비교 불가(이전 기간 데이터 없음)%` 라는 문장이 된다.
     */
    @Test
    @DisplayName("템플릿이 증감률 자리 뒤에 % 를 붙이지 않는다")
    fun templateDoesNotAppendPercent() {
        val template = PromptTemplates.WEEKLY_DIGEST_USER

        assertFalse(template.contains("{viewsChange}%"), "{viewsChange} 뒤에 % 가 붙어 있다")
        assertFalse(template.contains("{likesChange}%"), "{likesChange} 뒤에 % 가 붙어 있다")
        // 자리표시자가 사라지면 치환이 조용히 아무것도 하지 않는다.
        assertTrue(template.contains("{viewsChange}"), "{viewsChange} 자리표시자가 없다")
        assertTrue(template.contains("{likesChange}"), "{likesChange} 자리표시자가 없다")
    }

    /** 자리표시자가 하나라도 남으면 모델이 `{...}` 를 그대로 읽는다. */
    @Test
    @DisplayName("치환되지 않은 자리표시자가 프롬프트에 남지 않는다")
    fun noPlaceholderSurvives() {
        val prompt = renderedPrompt(
            kpi(totalViews = 50_000, totalViewsChange = null, totalLikes = 1_200, totalLikesChange = 5.0),
        )

        assertFalse(Regex("\\{\\w+}").containsMatchIn(prompt), "치환되지 않은 자리표시자가 남았다:\n$prompt")
    }

    // ── 절대 수치의 미측정 ───────────────────────────────────────────────────
    //
    // 증감률과 달리 **합계 자체**도 `null` 이 될 수 있다. 조회수·좋아요·댓글은
    // 그 지표를 수집하는 플랫폼의 행이 없으면 서버가 `null` 을 준다(Tumblr 는 노트
    // 총합을 `views` 에, Pinterest 는 저장 수를 `likes` 에 넣어 집계에서 빠진다).
    // `Long?.toString()` 은 문자열 `"null"` 을 만들고, 모델은 그것을 수치로 읽는다.

    /** **이 케이스가 "총 조회수: null" 을 유료 프롬프트에 보내던 자리다.** */
    @Test
    @DisplayName("조회수가 미측정이면 null 이 아니라 문장을 보낸다")
    fun unmeasuredViewsAreASentence() {
        val prompt = renderedPrompt(
            kpi(totalViews = null, totalViewsChange = null, totalLikes = 1_200, totalLikesChange = 5.0),
        )

        assertTrue(
            prompt.contains("총 조회수: ${MetricChange.NOT_MEASURED_TEXT}"),
            "미측정을 알리지 않았다:\n$prompt",
        )
        assertFalse(prompt.contains("총 조회수: 0"), "0 을 관측처럼 보냈다:\n$prompt")
        assertNoFabricatedNumbers(prompt)
    }

    @Test
    @DisplayName("좋아요가 미측정이면 null 이 아니라 문장을 보낸다")
    fun unmeasuredLikesAreASentence() {
        val prompt = renderedPrompt(
            kpi(totalViews = 50_000, totalViewsChange = 5.0, totalLikes = null, totalLikesChange = null),
        )

        assertTrue(prompt.contains("총 좋아요: ${MetricChange.NOT_MEASURED_TEXT}"), "미측정을 알리지 않았다:\n$prompt")
        assertNoFabricatedNumbers(prompt)
    }

    @Test
    @DisplayName("댓글이 미측정이면 null 이 아니라 문장을 보낸다")
    fun unmeasuredCommentsAreASentence() {
        val prompt = renderedPrompt(
            kpi(
                totalViews = 50_000,
                totalViewsChange = 5.0,
                totalLikes = 1_200,
                totalLikesChange = 5.0,
                totalComments = null,
            ),
        )

        assertTrue(prompt.contains("총 댓글: ${MetricChange.NOT_MEASURED_TEXT}"), "미측정을 알리지 않았다:\n$prompt")
        assertNoFabricatedNumbers(prompt)
    }

    /** **측정된 0 은 관측이다.** 문장으로 감추면 실제 관찰을 잃는다. */
    @Test
    @DisplayName("측정된 0 은 숫자 0 으로 그대로 보낸다")
    fun measuredZeroStaysANumber() {
        val prompt = renderedPrompt(
            kpi(totalViews = 0, totalViewsChange = null, totalLikes = 0, totalLikesChange = null, totalComments = 0),
        )

        assertTrue(prompt.contains("총 조회수: 0"), "실측 0 을 문장으로 감췄다:\n$prompt")
        assertTrue(prompt.contains("총 좋아요: 0"), "실측 0 을 문장으로 감췄다:\n$prompt")
        assertTrue(prompt.contains("총 댓글: 0"), "실측 0 을 문장으로 감췄다:\n$prompt")
        assertFalse(prompt.contains(MetricChange.NOT_MEASURED_TEXT), "실측 0 을 미측정으로 바꿨다:\n$prompt")
    }

    /** 미측정 문구에는 단위가 없다 — 뒤에 `%` 가 붙으면 문장이 깨진다. */
    @Test
    @DisplayName("미측정 문구 뒤에 단위가 붙지 않는다")
    fun notMeasuredTextCarriesNoUnit() {
        val prompt = renderedPrompt(
            kpi(totalViews = null, totalViewsChange = null, totalLikes = null, totalLikesChange = null),
        )

        assertFalse(prompt.contains("${MetricChange.NOT_MEASURED_TEXT}%"), "미측정 문구에 % 가 붙었다:\n$prompt")
    }
}
