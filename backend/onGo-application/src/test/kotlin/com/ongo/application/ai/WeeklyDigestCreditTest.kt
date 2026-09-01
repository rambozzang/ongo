package com.ongo.application.ai

import com.ongo.application.ai.result.WeeklyDigestResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.enums.PlanType
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.InsufficientCreditException
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.ai.WeeklyDigest
import com.ongo.domain.ai.WeeklyDigestRepository
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.DashboardKpi
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.video.Video
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * 주간 다이제스트의 **과금 경계**를 고정한다.
 *
 * `WeeklyDigestUseCase` 는 생성자에 [CreditService] 를 받고 `AiFeature.WEEKLY_DIGEST` 는
 * 8 크레딧으로 정의돼 있었는데, `generateDigest()` 안에서 **한 번도 부르지 않았다.**
 * Pro/Business 사용자 전원이 매주 월요일 09:00 스케줄러로 자동 실행되므로, 이 누수는
 * 구독자 수 × 8 크레딧이 매주 무료로 새어 나가는 구조였다.
 *
 * 컴파일은 통과하고 테스트도 전부 초록이었다 — 아무도 "차감했는가"를 묻지 않았기 때문이다.
 * relaxed mock 은 이 질문에 답하지 못한다. 여기서는 실제 호출 인자와 횟수를 본다.
 */
class WeeklyDigestCreditTest {

    private val chatClientResolver = mockk<ChatClientResolver>()
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

    private val savedDigest = WeeklyDigest(
        id = 99,
        userId = 7L,
        weekStartDate = LocalDate.of(2026, 8, 17),
        weekEndDate = LocalDate.of(2026, 8, 23),
        summary = "요약",
        topVideos = "",
        anomalies = "",
        actionItems = "",
    )

    /** KPI 조회는 차감 **전** 단계다. 여기서 실패하면 AI 를 안 불렀으니 과금도 없다. */
    private fun givenAnalytics() {
        every { analyticsRepository.getDashboardKpi(userId, 7) } returns DashboardKpi(
            totalViews = 50_000,
            totalViewsChange = 12.0,
            totalSubscribers = 100,
            totalSubscribersChange = 12,
            totalLikes = 1_200,
            totalLikesChange = 5.0,
            creditBalance = 500,
            creditTotal = 1000,
            totalComments = 34,
        )
        every { analyticsRepository.getTopVideos(userId, 7, 3) } returns listOf(
            Video(id = 1, userId = userId, title = "테스트 영상"),
        )
    }

    /** 차감이 통과했다고 보고 블록을 실제로 실행한다. */
    private fun givenCreditsPass() {
        every { creditService.withCredits(userId, AiFeature.WEEKLY_DIGEST, any<() -> WeeklyDigest>()) } answers {
            thirdArg<() -> WeeklyDigest>().invoke()
        }
    }

    private fun givenAiReturns(result: WeeklyDigestResult) {
        val requestSpec = mockk<ChatClient.ChatClientRequestSpec>()
        val callSpec = mockk<ChatClient.CallResponseSpec>()
        val chatClient = mockk<ChatClient>()
        every { chatClientResolver.resolve(userId) } returns chatClient
        every { chatClient.prompt() } returns requestSpec
        every { requestSpec.system(any<String>()) } returns requestSpec
        every { requestSpec.user(any<String>()) } returns requestSpec
        every { requestSpec.call() } returns callSpec
        every { callSpec.entity(WeeklyDigestResult::class.java) } returns result
    }

    private fun givenAiFails(failure: Throwable) {
        val requestSpec = mockk<ChatClient.ChatClientRequestSpec>()
        val callSpec = mockk<ChatClient.CallResponseSpec>()
        val chatClient = mockk<ChatClient>()
        every { chatClientResolver.resolve(userId) } returns chatClient
        every { chatClient.prompt() } returns requestSpec
        every { requestSpec.system(any<String>()) } returns requestSpec
        every { requestSpec.user(any<String>()) } returns requestSpec
        every { requestSpec.call() } returns callSpec
        every { callSpec.entity(WeeklyDigestResult::class.java) } throws failure
    }

    private fun aiResult() = WeeklyDigestResult(
        summary = "요약",
        topVideos = listOf(WeeklyDigestResult.TopVideoInsight("테스트 영상", 100, "인사이트")),
        anomalies = listOf("특이사항 없음"),
        actionItems = listOf("다음 주 업로드 2회"),
    )

    // ── 차감이 실제로 일어난다 ────────────────────────────────────────────────

    @Test
    @DisplayName("다이제스트 생성은 WEEKLY_DIGEST 크레딧을 정확히 1회 차감한다")
    fun digestChargesExactlyOnce() {
        givenAnalytics()
        givenCreditsPass()
        givenAiReturns(aiResult())
        every { weeklyDigestRepository.save(any()) } returns savedDigest

        useCase.generateDigest(userId, weekStart, weekEnd)

        // 기능과 사용자를 함께 고정한다. 다른 AiFeature 로 바뀌면 요금이 조용히 달라진다.
        verify(exactly = 1) {
            creditService.withCredits(userId, AiFeature.WEEKLY_DIGEST, any<() -> WeeklyDigest>())
        }
    }

    @Test
    @DisplayName("차감 블록의 결과가 그대로 반환된다")
    fun blockResultIsReturned() {
        givenAnalytics()
        givenCreditsPass()
        givenAiReturns(aiResult())
        every { weeklyDigestRepository.save(any()) } returns savedDigest

        val returned = useCase.generateDigest(userId, weekStart, weekEnd)

        // withCredits 를 껍데기로 감싸고 결과를 버리면 여기서 걸린다.
        assertSame(savedDigest, returned)
    }

    /** 요금은 enum 하나에서 온다. 8 이 아닌 값으로 바뀌면 청구가 달라진다. */
    @Test
    @DisplayName("WEEKLY_DIGEST 요금은 8 크레딧이다")
    fun featureCostIsEight() {
        assertEquals(8, AiFeature.WEEKLY_DIGEST.creditCost)
    }

    // ── 실패 시 경계 ─────────────────────────────────────────────────────────

    /**
     * AI 실패는 **블록 안에서** 나야 [CreditService.withCredits] 가 환불한다.
     * 저장을 블록 밖으로 빼거나 예외를 블록 안에서 삼키면 환불 경계가 깨진다.
     */
    @Test
    @DisplayName("블록 안 AI 실패는 밖으로 전달되고 저장은 일어나지 않는다")
    fun aiFailureInsideBlockPropagatesAndSkipsSave() {
        givenAnalytics()
        givenCreditsPass()
        givenAiFails(RuntimeException("모델 타임아웃"))

        val e = assertFailsWith<BusinessException> {
            useCase.generateDigest(userId, weekStart, weekEnd)
        }

        assertEquals("AI_CALL_FAILED", e.code)
        verify(exactly = 0) { weeklyDigestRepository.save(any()) }
        // 예외가 withCredits 블록을 뚫고 나와야 환불 로직이 돈다.
        verify(exactly = 1) {
            creditService.withCredits(userId, AiFeature.WEEKLY_DIGEST, any<() -> WeeklyDigest>())
        }
    }

    /**
     * 잔액 부족은 블록이 돌기 **전에** 던져진다. `AI_CALL_FAILED` 로 둔갑하면 사용자는
     * 충전하면 된다는 것을 알 수 없고, 운영은 AI 장애로 오인한다.
     */
    @Test
    @DisplayName("잔액이 부족하면 AI 를 부르지도 저장하지도 않는다")
    fun insufficientCreditsSkipAiAndSave() {
        givenAnalytics()
        every {
            creditService.withCredits(userId, AiFeature.WEEKLY_DIGEST, any<() -> WeeklyDigest>())
        } throws InsufficientCreditException(required = 8, available = 2)

        val e = assertFailsWith<InsufficientCreditException> {
            useCase.generateDigest(userId, weekStart, weekEnd)
        }

        assertEquals("CREDIT_INSUFFICIENT", e.code)
        verify(exactly = 0) { chatClientResolver.resolve(any()) }
        verify(exactly = 0) { weeklyDigestRepository.save(any()) }
    }

    // ── 스케줄러도 같은 경로를 탄다 ───────────────────────────────────────────

    /**
     * 이 누수의 규모를 만든 것이 스케줄러다. 매주 Pro/Business 전원이 자동 실행되므로
     * 스케줄러가 차감 없는 우회 경로를 갖고 있으면 수정이 무의미해진다.
     */
    @Test
    @DisplayName("스케줄러 자동 생성도 같은 차감 경로를 탄다")
    fun schedulerGoesThroughTheSameChargePath() {
        givenAnalytics()
        givenCreditsPass()
        givenAiReturns(aiResult())
        every { weeklyDigestRepository.save(any()) } returns savedDigest

        val subscriptionRepository = mockk<SubscriptionRepository>()
        every { subscriptionRepository.findByPlanType(PlanType.PRO) } returns
            listOf(Subscription(id = userId, userId = userId, planType = PlanType.PRO))
        every { subscriptionRepository.findByPlanType(PlanType.BUSINESS) } returns emptyList()
        val schedulerGuard = mockk<UserWriteGuard>(relaxed = true)

        // 유스케이스를 mock 으로 갈아끼우지 않는다. 그러면 차감 여부를 확인할 수 없다.
        WeeklyDigestScheduler(subscriptionRepository, useCase, schedulerGuard).generateWeeklyDigests()

        verify(exactly = 1) {
            creditService.withCredits(userId, AiFeature.WEEKLY_DIGEST, any<() -> WeeklyDigest>())
        }
        verify(exactly = 1) { weeklyDigestRepository.save(any()) }
    }

    @Test
    @DisplayName("스케줄러에서 잔액이 부족하면 AI 호출도 저장도 없이 배치가 계속된다")
    fun schedulerSkipsUsersWithoutCredits() {
        givenAnalytics()
        every {
            creditService.withCredits(userId, AiFeature.WEEKLY_DIGEST, any<() -> WeeklyDigest>())
        } throws InsufficientCreditException(required = 8, available = 0)

        val subscriptionRepository = mockk<SubscriptionRepository>()
        every { subscriptionRepository.findByPlanType(PlanType.PRO) } returns
            listOf(Subscription(id = userId, userId = userId, planType = PlanType.PRO))
        every { subscriptionRepository.findByPlanType(PlanType.BUSINESS) } returns emptyList()
        val schedulerGuard = mockk<UserWriteGuard>(relaxed = true)

        // 잔액 부족으로 배치 전체가 죽으면 뒤 사용자들이 다이제스트를 못 받는다.
        WeeklyDigestScheduler(subscriptionRepository, useCase, schedulerGuard).generateWeeklyDigests()

        verify(exactly = 0) { chatClientResolver.resolve(any()) }
        verify(exactly = 0) { weeklyDigestRepository.save(any()) }
    }
}
