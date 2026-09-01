package com.ongo.application.revenue

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.result.RevenueInsightResult
import com.ongo.application.credit.CreditService
import com.ongo.application.revenue.dto.PlatformRevenueItem
import com.ongo.application.revenue.dto.RevenueInsightResponse
import com.ongo.application.revenue.dto.RevenueSummaryResponse
import com.ongo.common.enums.AiFeature
import com.ongo.domain.revenue.RevenueInsight
import com.ongo.domain.revenue.RevenueInsightRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 수익 인사이트 **유료 프롬프트**에 정의되지 않은 비중이 새지 않는지 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * "${pr.platform}: ${pr.revenueKrw}원 (${pr.percentage}%)"
 * ```
 *
 * `percentage` 는 전체 수익이 0 이면 비율이 성립하지 않는다. 예전 서버는 그 자리에
 * `0.0` 을 넣어 **"비중 0%"** 가 프롬프트에 들어갔고, 모델은 그것을 관측으로 읽고 없는
 * 수익 구성을 설명했다.
 *
 * 이제 서버가 `null` 을 준다. 그런데 Kotlin 문자열 템플릿의 `${null}` 은 **문자열
 * `"null"`** 이라, 그대로 두면 `"null%"` 가 나간다. 유료 호출이라 대가까지 치른다.
 */
class RevenueInsightPromptMeasurementTest {

    private val revenueInsightRepository = mockk<RevenueInsightRepository>(relaxed = true)
    private val revenueUseCase = mockk<RevenueUseCase>()
    private val chatClientResolver = mockk<ChatClientResolver>()

    /**
     * **relaxed mock 으로 두면 안 된다.** `withCredits` 는 블록을 실행해 결과를 돌려주는
     * 함수인데, relaxed mock 은 블록을 부르지 않는다. 그러면 AI 도 호출되지 않아
     * 아래 검증이 아무것도 검사하지 않으면서 통과한다.
     */
    private val creditService = mockk<CreditService>()

    private val useCase = RevenueInsightUseCase(
        revenueInsightRepository = revenueInsightRepository,
        revenueUseCase = revenueUseCase,
        chatClientResolver = chatClientResolver,
        creditService = creditService,
        rateLimiter = mockk<AiRateLimiter>(relaxed = true),
        objectMapper = ObjectMapper(),
    )

    private val userId = 7L

    private fun summary(vararg breakdown: PlatformRevenueItem) = RevenueSummaryResponse(
        totalRevenue = breakdown.sumOf { it.revenueMicro },
        totalRevenueKrw = breakdown.sumOf { it.revenueKrw },
        growthPercent = 5.0,
        platformBreakdown = breakdown.toList(),
        platformRevenueAvailable = true,
        platformRevenueUnavailableReason = null,
        platformRevenueReconnectRequired = false,
    )

    private fun item(platform: String, krw: Long, percentage: Double?) = PlatformRevenueItem(
        platform = platform,
        revenueMicro = krw * 1_000_000,
        revenueKrw = krw,
        percentage = percentage,
    )

    /** 유스케이스를 실제로 돌리고 AI 에게 간 user 프롬프트를 돌려준다. */
    private fun renderedPrompt(vararg breakdown: PlatformRevenueItem): String {
        every { revenueUseCase.getRevenueSummary(userId, 30) } returns summary(*breakdown)
        every {
            creditService.withCredits(
                userId,
                AiFeature.REVENUE_INSIGHT,
                any<() -> RevenueInsightResponse>(),
            )
        } answers { thirdArg<() -> RevenueInsightResponse>().invoke() }
        every { revenueInsightRepository.save(any()) } answers {
            firstArg<RevenueInsight>().copy(id = 1L)
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
        every { callSpec.entity(RevenueInsightResult::class.java) } returns RevenueInsightResult(
            insightType = "SUMMARY",
            summary = "요약",
            details = emptyList(),
            recommendations = emptyList(),
            confidence = 0.8,
        )

        useCase.generateInsight(userId)

        assertTrue(userPrompt.isCaptured, "AI user 프롬프트를 붙잡지 못했다")
        return userPrompt.captured
    }

    /** **이 케이스가 "null%" 를 유료 프롬프트에 보내던 자리다.** */
    @Test
    @DisplayName("비중이 null 이면 null% 가 아니라 문장을 보낸다")
    fun unmeasuredShareBecomesASentence() {
        val prompt = renderedPrompt(item("YOUTUBE", krw = 0, percentage = null))

        assertTrue(
            RevenueInsightUseCase.SHARE_NOT_MEASURED in prompt,
            "미측정을 알리지 않았다:\n$prompt",
        )
        assertFalse("null" in prompt, "프롬프트에 'null' 이 남았다:\n$prompt")
        assertFalse("(0.0%)" in prompt, "정의되지 않은 비중을 0% 로 보냈다:\n$prompt")
        assertFalse("NaN" in prompt)
    }

    /** **분모가 양수일 때의 0% 는 실측이다.** 문장으로 감추면 관찰을 잃는다. */
    @Test
    @DisplayName("측정된 0퍼센트 비중은 숫자로 보낸다")
    fun measuredZeroShareStaysANumber() {
        val prompt = renderedPrompt(
            item("YOUTUBE", krw = 100, percentage = 100.0),
            item("TIKTOK", krw = 0, percentage = 0.0),
        )

        assertTrue("TIKTOK: 0원 (0.0%)" in prompt, "실측 0% 를 문장으로 감췄다:\n$prompt")
        assertFalse(RevenueInsightUseCase.SHARE_NOT_MEASURED in prompt)
    }

    @Test
    @DisplayName("측정된 비중은 그대로 보낸다")
    fun measuredShareReachesThePrompt() {
        val prompt = renderedPrompt(item("YOUTUBE", krw = 250, percentage = 25.0))

        assertTrue("YOUTUBE: 250원 (25.0%)" in prompt, "측정값이 훼손됐다:\n$prompt")
    }

    /** 단위(`%`)는 값이 들고 온다 — 밖에 붙어 있으면 "…불가(…)%" 가 된다. */
    @Test
    @DisplayName("미측정 문구 뒤에 퍼센트 기호가 붙지 않는다")
    fun notMeasuredTextCarriesNoUnit() {
        val prompt = renderedPrompt(item("YOUTUBE", krw = 0, percentage = null))

        assertFalse("${RevenueInsightUseCase.SHARE_NOT_MEASURED}%" in prompt, "미측정 문구에 % 가 붙었다")
    }

    /** 치환되지 않은 자리표시자가 남으면 모델이 `{...}` 를 그대로 읽는다. */
    @Test
    @DisplayName("치환되지 않은 자리표시자가 남지 않는다")
    fun noPlaceholderSurvives() {
        val prompt = renderedPrompt(item("YOUTUBE", krw = 0, percentage = null))

        assertFalse(Regex("\\{\\w+}").containsMatchIn(prompt), "자리표시자가 남았다:\n$prompt")
    }
}
