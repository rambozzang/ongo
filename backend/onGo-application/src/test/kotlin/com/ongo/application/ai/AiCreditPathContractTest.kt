package com.ongo.application.ai

import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 동기식 유료 AI 경로의 과금 계약.
 *
 * 유스케이스마다 차감·환불을 따로 구현하던 시절에는 분기마다 규칙이 달랐다. 특히
 * `catch (BusinessException) { throw e }` 는 환불 없이 그대로 올라가서, `AI_PARSE_ERROR`
 * 로 끝난 호출은 **결과 없이 크레딧만 사라졌다.**
 *
 * 지금은 `CreditService.withCredits` 한 곳이 그 보장을 갖는다. 그래서 각 유스케이스에서
 * 고정해야 하는 것은 두 가지다.
 *
 *  1. 공개 경로가 **그 공통 경로를 탄다**
 *  2. 실패가 블록 **밖으로 전파된다** — 안에서 삼키면 환불이 일어나지 않는다
 *
 * 환불이 실제로 일어나는지는 `CreditServiceTest` 가 검증한다. 여기서 그것을 다시
 * 검증한다고 주장하지 않는다.
 */
class AiCreditPathContractTest {

    private val chatClientResolver = mockk<ChatClientResolver>()
    private val creditService = mockk<CreditService>()
    private val rateLimiter = mockk<AiRateLimiter>(relaxed = true)

    private val userId = 7L

    /** 차감이 성공한 경우. 블록을 그대로 실행한다. */
    private fun grantCredits(feature: AiFeature) {
        every { creditService.withCredits(userId, feature, any<() -> Any>()) } answers {
            @Suppress("UNCHECKED_CAST")
            (thirdArg<() -> Any>())()
        }
    }

    // ---- AnalyzeScript ----

    private fun analyzeScript() = AnalyzeScriptUseCase(
        chatClientResolver = chatClientResolver,
        creditService = creditService,
        rateLimiter = rateLimiter,
    )

    @Test
    fun `스크립트 분석 실패는 공통 크레딧 경로 밖으로 전파된다`() {
        grantCredits(AiFeature.SCRIPT_ANALYSIS)
        every { chatClientResolver.resolve(userId) } throws IllegalStateException("모델 장애")

        val error = assertFailsWith<BusinessException> {
            analyzeScript().execute(userId, "대본")
        }

        assertEquals("AI_CALL_FAILED", error.code)
        verify(exactly = 1) {
            creditService.withCredits(userId, AiFeature.SCRIPT_ANALYSIS, any<() -> Any>())
        }
        verify(exactly = 0) { creditService.validateAndDeduct(any(), any<AiFeature>()) }
        verify(exactly = 0) { creditService.refundAllocation(any()) }
    }

    /**
     * 파이프라인 경로는 이미 예약된 크레딧으로 돈다. 여기서 또 차감하면 이중 과금이다.
     * 레이트 리밋도 걸지 않는다 — 사용자가 직접 부른 호출이 아니다.
     */
    @Test
    fun `스크립트 분석 내부 경로는 크레딧을 건드리지 않는다`() {
        every { chatClientResolver.resolve(userId) } throws IllegalStateException("모델 장애")

        // 내부 경로는 예외를 감싸지 않고 그대로 올린다(호출자인 파이프라인이 정산한다).
        assertFailsWith<IllegalStateException> {
            analyzeScript().executeInternal(userId, "대본")
        }

        verify(exactly = 0) { creditService.withCredits(any(), any<AiFeature>(), any<() -> Any>()) }
        verify(exactly = 0) { creditService.validateAndDeduct(any(), any<AiFeature>()) }
        verify(exactly = 0) { creditService.refundAllocation(any()) }
        verify(exactly = 0) { rateLimiter.checkRateLimit(any()) }
    }

    // ---- StrategyCoach ----

    @Test
    fun `전략 코치 실패는 공통 크레딧 경로 밖으로 전파된다`() {
        val analytics = mockk<com.ongo.domain.analytics.AnalyticsRepository>(relaxed = true)
        val videos = mockk<com.ongo.domain.video.VideoRepository>(relaxed = true)
        val competitors = mockk<com.ongo.domain.competitor.CompetitorRepository>(relaxed = true)
        grantCredits(AiFeature.STRATEGY_COACH)
        every { chatClientResolver.resolve(userId) } throws IllegalStateException("모델 장애")

        val useCase = StrategyCoachUseCase(
            chatClientResolver = chatClientResolver,
            creditService = creditService,
            rateLimiter = rateLimiter,
            analyticsRepository = analytics,
            videoRepository = videos,
            competitorRepository = competitors,
        )

        val error = assertFailsWith<BusinessException> { useCase.execute(userId) }

        assertEquals("AI_CALL_FAILED", error.code)
        verify(exactly = 1) {
            creditService.withCredits(userId, AiFeature.STRATEGY_COACH, any<() -> Any>())
        }
        verify(exactly = 0) { creditService.refundAllocation(any()) }
    }
}
