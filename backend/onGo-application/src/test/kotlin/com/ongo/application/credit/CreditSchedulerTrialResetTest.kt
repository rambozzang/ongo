package com.ongo.application.credit

import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.domain.credit.AiCredit
import com.ongo.domain.credit.AiCreditTransaction
import com.ongo.domain.credit.CreditRepository
import com.ongo.domain.lock.DistributedLockPort
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 월간 무료 크레딧 리셋이 **체험 중인 사용자를 건너뛰는지** 고정한다.
 *
 * ## 막으려는 것
 *
 * 리셋은 `freeRemaining` 을 `freeMonthly` 로 되돌린다. 체험 중에는 그 값이 STARTER 기준
 * 100 이라, 말일에 체험을 시작하면 1월 28일에 100 을 받고 2월 1일 배치에서 100 을 또
 * 받는다. 7일 체험에 200 이다.
 *
 * ## 시간을 고정하지 않는 이유
 *
 * 판단 기준이 시각이 아니라 **구독 상태**다. `LocalDate.now()` 는 어느 사용자를 대상으로
 * 잡을지에만 쓰이고, 대상 목록은 테스트가 직접 준다. 그래서 Clock 을 새로 들이지 않고도
 * 경계를 전부 표현할 수 있다.
 */
class CreditSchedulerTrialResetTest {

    private val creditRepository = mockk<CreditRepository>()
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val distributedLock = mockk<DistributedLockPort>()
    private val transactionManager = mockk<PlatformTransactionManager>()

    private val userId = 7L
    private val today = LocalDate.of(2026, 2, 1)

    /*
     * 두 경계는 **실행 시점 기준**이어야 한다. 고정 날짜를 쓰면 벽시계가 그 날짜를 지난
     * 뒤에는 "체험이 아직 유효한" 픽스처가 조용히 "이미 끝난" 픽스처가 되고, 두 테스트가
     * 같은 상황을 검사하게 된다. 그러면 trialEnd 기반 가드와 상태 기반 가드를 구분하지
     * 못한다.
     */
    private val trialStillValid: LocalDateTime get() = LocalDateTime.now().plusDays(3)
    private val trialAlreadyOver: LocalDateTime get() = LocalDateTime.now().minusHours(2)

    private val scheduler = CreditScheduler(
        creditRepository = creditRepository,
        eventPublisher = eventPublisher,
        distributedLock = distributedLock,
        subscriptionRepository = subscriptionRepository,
        transactionManager = transactionManager,
    )

    init {
        // 락은 항상 잡히고, 트랜잭션은 그대로 실행된다.
        every { distributedLock.withLock(any(), any()) } answers {
            secondArg<() -> Unit>().invoke()
            true
        }
        every { transactionManager.getTransaction(any()) } returns SimpleTransactionStatus()
        every { transactionManager.commit(any<TransactionStatus>()) } just runs
        every { transactionManager.rollback(any<TransactionStatus>()) } just runs
        every { creditRepository.findUsersForFreeReset(any()) } returns listOf(userId)
    }

    private fun subscription(
        status: SubscriptionStatus,
        planType: PlanType,
        trialEnd: LocalDateTime? = null,
    ) = Subscription(
        id = 1, userId = userId, planType = planType, status = status,
        trialStart = trialEnd?.minusDays(7), trialEnd = trialEnd,
    )

    private fun credit(freeMonthly: Int, freeRemaining: Int) = AiCredit(
        id = 1, userId = userId, balance = freeRemaining,
        freeMonthly = freeMonthly, freeRemaining = freeRemaining,
        freeResetDate = today,
    )

    /**
     * (a) 말일 시작 → 다음 달 1일 배치. 체험이 아직 유효하다.
     *
     * 잔액도 원장도 건드리지 않아야 한다. 크레딧 조회조차 하지 않는다 —
     * `FOR UPDATE` 로 잡고 아무것도 안 하는 것보다 아예 손대지 않는 편이 명확하다.
     */
    @Test
    fun `체험이 유효한 사용자는 월간 리셋에서 잔액도 원장도 바뀌지 않는다`() {
        every { subscriptionRepository.findByUserId(userId) } returns
            subscription(SubscriptionStatus.TRIALING, PlanType.STARTER, trialEnd = trialStillValid)

        scheduler.resetFreeCredits()

        verify(exactly = 0) { creditRepository.findByUserIdForUpdate(any()) }
        verify(exactly = 0) { creditRepository.update(any()) }
        verify(exactly = 0) { creditRepository.saveTransaction(any()) }
    }

    /**
     * `trialEnd` 는 지났는데 상태가 아직 TRIALING 인 구간. 리셋은 1일 00:00,
     * 만료 처리는 매일 02:00 이라 매달 최소 두 시간 존재한다.
     *
     * 이때 `freeMonthly` 는 여전히 체험 플랜 값(100)이므로 리셋하면 같은 이중 지급이
     * 일어난다. 상태가 정산되기 전에는 건너뛴다.
     */
    @Test
    fun `만료일이 지났어도 상태가 TRIALING 이면 리셋하지 않는다`() {
        every { subscriptionRepository.findByUserId(userId) } returns
            subscription(SubscriptionStatus.TRIALING, PlanType.STARTER, trialEnd = trialAlreadyOver)
        /*
         * 리셋 경로를 전부 스텁해 둔다. 스텁이 없으면 가드가 사라졌을 때 mock 이 먼저
         * 터지고, 그 예외를 스케줄러의 사용자별 catch 가 삼켜서 update 가 호출되지 않는다.
         * 그러면 이 테스트는 가드가 아니라 스텁 누락 덕분에 통과한다.
         *
         * freeMonthly 는 아직 체험 플랜 값(100)이다 — 정산 전 구간이라는 것이 요점이다.
         */
        every { creditRepository.findByUserIdForUpdate(userId) } returns
            credit(freeMonthly = PlanType.STARTER.freeCredits, freeRemaining = 4)
        every { creditRepository.findActivePurchasedCredits(userId) } returns emptyList()
        every { creditRepository.update(any()) } answers { firstArg() }
        every { creditRepository.saveTransaction(any()) } answers { firstArg() }

        scheduler.resetFreeCredits()

        verify(exactly = 0) { creditRepository.update(any()) }
        verify(exactly = 0) { creditRepository.saveTransaction(any()) }
    }

    /** (b) 체험과 무관한 FREE 사용자의 정상 리셋은 그대로 동작해야 한다. */
    @Test
    fun `체험 중이 아닌 FREE 사용자는 정상적으로 리셋된다`() {
        every { subscriptionRepository.findByUserId(userId) } returns
            subscription(SubscriptionStatus.FREE, PlanType.FREE)
        every { creditRepository.findByUserIdForUpdate(userId) } returns
            credit(freeMonthly = PlanType.FREE.freeCredits, freeRemaining = 3)
        every { creditRepository.findActivePurchasedCredits(userId) } returns emptyList()
        val updated = slot<AiCredit>()
        val tx = slot<AiCreditTransaction>()
        every { creditRepository.update(capture(updated)) } answers { updated.captured }
        every { creditRepository.saveTransaction(capture(tx)) } answers { tx.captured }

        scheduler.resetFreeCredits()

        assertEquals(PlanType.FREE.freeCredits, updated.captured.freeRemaining)
        assertEquals(PlanType.FREE.freeCredits, updated.captured.balance)
        /*
         * 다음 리셋 주기가 다음 달 1일로 밀린다.
         *
         * 스케줄러가 내부에서 LocalDate.now() 를 쓰므로 고정 날짜로 비교할 수 없다.
         * 이 변경은 시각이 아니라 구독 상태로 판단하므로 Clock 을 새로 들이지 않고,
         * 실행 시점 기준 상대값으로 확인한다.
         */
        assertEquals(
            LocalDate.now().plusMonths(1).withDayOfMonth(1),
            updated.captured.freeResetDate,
        )
        assertEquals(PlanType.FREE.freeCredits, tx.captured.amount)
        assertEquals("MONTHLY_RESET", tx.captured.feature)
    }

    /**
     * (c-1) 체험이 끝나 FREE 로 정산된 사용자. `applyPlanEntitlement` 가 `freeMonthly` 를
     * 30 으로 내려둔 상태이며, 다음 정상 리셋이 그 값으로 이뤄져야 한다.
     */
    @Test
    fun `체험이 끝난 사용자는 FREE 기준으로 정상 리셋된다`() {
        every { subscriptionRepository.findByUserId(userId) } returns
            subscription(SubscriptionStatus.FREE, PlanType.FREE, trialEnd = LocalDateTime.of(2026, 1, 10, 0, 0))
        every { creditRepository.findByUserIdForUpdate(userId) } returns
            credit(freeMonthly = PlanType.FREE.freeCredits, freeRemaining = 0)
        every { creditRepository.findActivePurchasedCredits(userId) } returns emptyList()
        val updated = slot<AiCredit>()
        every { creditRepository.update(capture(updated)) } answers { updated.captured }
        every { creditRepository.saveTransaction(any()) } answers { firstArg() }

        scheduler.resetFreeCredits()

        assertEquals(PlanType.FREE.freeCredits, updated.captured.freeRemaining)
    }

    /**
     * (c-2) 유료 활성 구독. 체험이 아니므로 리셋 대상이며, 플랜 기준 무료 크레딧을
     * 그대로 받아야 한다. 여기까지 막으면 돈을 낸 사용자의 월간 크레딧이 사라진다.
     */
    @Test
    fun `유료 활성 구독은 플랜 기준으로 정상 리셋된다`() {
        every { subscriptionRepository.findByUserId(userId) } returns
            subscription(SubscriptionStatus.ACTIVE, PlanType.PRO)
        every { creditRepository.findByUserIdForUpdate(userId) } returns
            credit(freeMonthly = PlanType.PRO.freeCredits, freeRemaining = 12)
        every { creditRepository.findActivePurchasedCredits(userId) } returns emptyList()
        val updated = slot<AiCredit>()
        every { creditRepository.update(capture(updated)) } answers { updated.captured }
        every { creditRepository.saveTransaction(any()) } answers { firstArg() }

        scheduler.resetFreeCredits()

        assertEquals(PlanType.PRO.freeCredits, updated.captured.freeRemaining)
    }

    /**
     * 구독 행이 없는 사용자를 체험으로 오인해 막으면 안 된다. 리셋을 막는 판단은
     * 확실할 때만 해야 한다 — 잘못 막으면 정상 사용자의 월간 크레딧이 사라진다.
     */
    @Test
    fun `구독 정보가 없으면 리셋을 막지 않는다`() {
        every { subscriptionRepository.findByUserId(userId) } returns null
        every { creditRepository.findByUserIdForUpdate(userId) } returns
            credit(freeMonthly = PlanType.FREE.freeCredits, freeRemaining = 0)
        every { creditRepository.findActivePurchasedCredits(userId) } returns emptyList()
        val updated = slot<AiCredit>()
        every { creditRepository.update(capture(updated)) } answers { updated.captured }
        every { creditRepository.saveTransaction(any()) } answers { firstArg() }

        scheduler.resetFreeCredits()

        assertEquals(PlanType.FREE.freeCredits, updated.captured.freeRemaining)
    }

    /**
     * 건너뛴 사용자의 `freeResetDate` 를 앞당기지 않아야 다음 달 배치가 다시 잡는다.
     * 여기서 날짜만 밀어두면 체험이 끝난 뒤 한 달을 통째로 건너뛴다.
     */
    @Test
    fun `건너뛴 사용자의 다음 리셋 주기를 앞당기지 않는다`() {
        every { subscriptionRepository.findByUserId(userId) } returns
            subscription(SubscriptionStatus.TRIALING, PlanType.STARTER, trialEnd = trialStillValid)

        scheduler.resetFreeCredits()

        // freeResetDate 를 바꾸는 유일한 경로가 update 다. 호출이 없으면 주기도 그대로다.
        verify(exactly = 0) { creditRepository.update(any()) }
    }
}
