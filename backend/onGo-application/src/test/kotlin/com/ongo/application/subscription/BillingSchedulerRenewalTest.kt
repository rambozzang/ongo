package com.ongo.application.subscription

import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.domain.credit.CreditRepository
import com.ongo.domain.lock.DistributedLockPort
import com.ongo.domain.notification.NotificationRepository
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRenewalOutcome
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 스케줄러가 갱신 대상을 **실제로 조회하고 건별로 처리하는지** 고정한다.
 *
 * `findDueForBilling` 은 저장소에 구현돼 있었지만 부르는 코드가 어디에도 없었다. 그래서
 * 결제 주기가 지나도 아무 일도 일어나지 않았고, 한 번 결제한 고객이 영구히 유료 플랜을 썼다.
 *
 * 여기서 검증하는 것은 오케스트레이션이다 — 청구 판정 자체는
 * [SubscriptionRenewalServiceTest] 가 본다.
 */
class BillingSchedulerRenewalTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val notificationRepository = mockk<NotificationRepository>(relaxed = true)
    private val creditRepository = mockk<CreditRepository>(relaxed = true)
    private val creditService = mockk<com.ongo.application.credit.CreditService>(relaxed = true)
    private val distributedLockPort = mockk<DistributedLockPort>()
    private val renewalService = mockk<SubscriptionRenewalService>()

    private fun scheduler(renewalEnabled: Boolean = true) = BillingScheduler(
        subscriptionRepository,
        userRepository,
        notificationRepository,
        creditRepository,
        distributedLockPort,
        creditService,
        renewalService,
        DummyTransactionManagerForTest(),
        renewalEnabled,
    )

    private val scheduler = scheduler()

    /**
     * 갱신 경로가 트랜잭션에 감싸이면 PG 호출 동안 커넥션이 잠기고, 청구 성공 후 롤백이
     * 선점 기록을 지워 다음 실행이 재청구한다. 스케줄러가 그 경계를 만들지 않아야 한다.
     */
    @Test
    fun `스케줄러는 갱신을 트랜잭션으로 감싸지 않는다`() {
        /*
         * 트랜잭션 템플릿을 **허용 목록**으로 고정한다. 이름을 세는 방식이라 거칠지만,
         * 목적이 "갱신 근처에 트랜잭션 경계를 새로 만들 때 반드시 한 번 멈춰 서게 하는
         * 것" 이므로 이 거칠음이 곧 기능이다. 새 템플릿을 추가하려면 여기 이름을 적고
         * 그것이 갱신 경로를 감싸지 않는다는 것을 스스로 확인해야 한다.
         *
         * 동작(활성 트랜잭션 유무)으로 보는 방식은 쓰지 않는다 —
         * DummyTransactionManagerForTest 가 TransactionSynchronizationManager 에 등록하지
         * 않아 언제나 "없음" 이 나오고, 갱신을 감싸도 통과하는 공허한 검사가 된다.
         *
         * - perItemTx: 건별 격리(REQUIRES_NEW). 하향 적용·기존 처리의 각 건에만 쓰이고
         *   `renewalService.renew` 호출은 어떤 템플릿 안에도 들어가지 않는다.
         *
         * 단계 전체를 감싸던 legacyTx 는 제거됐다 — 모든 쓰기가 건별 REQUIRES_NEW 로
         * 들어가면서 바깥 경계는 매 건 suspend/resume 과 커넥션 점유만 남았다.
         */
        val fields = BillingScheduler::class.java.declaredFields.map { it.name }
        assertEquals(
            listOf("perItemTx"),
            fields.filter { it.endsWith("Tx") },
            "갱신을 감싸는 트랜잭션 템플릿이 생겼다: $fields",
        )
    }

    /**
     * 건별 격리 경계는 **`REQUIRES_NEW`** 여야 한다. `REQUIRED` 면 바깥 트랜잭션에 참여해,
     * 한 건의 실패가 바깥을 rollbackOnly 로 만들고 배치 전체가 되돌아간다 — 예외를 삼켜도
     * 소용없는 가짜 격리가 된다.
     */
    @Test
    fun `건별 격리 경계는 새 트랜잭션을 연다`() {
        val perItemTx = BillingScheduler::class.java.getDeclaredField("perItemTx")
            .apply { isAccessible = true }
            .get(scheduler) as TransactionTemplate

        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, perItemTx.propagationBehavior)
    }

    private fun subscription(id: Long) = Subscription(
        id = id,
        userId = id * 10,
        planType = PlanType.PRO,
        status = SubscriptionStatus.ACTIVE,
        price = 19_900,
        billingCycle = BillingCycle.MONTHLY,
        currentPeriodEnd = LocalDateTime.parse("2026-09-01T00:00:00"),
        nextBillingDate = LocalDateTime.parse("2026-09-01T00:00:00"),
    )

    /* ---- 롤아웃 게이트 ---- */

    /**
     * 운영 Flyway 는 V93 이고 빌링키를 등록한 고객이 하나도 없다. 배포만으로 갱신이
     * 시작되면 기존 ACTIVE 구독이 전부 BILLING_KEY_MISSING → PAST_DUE → 7일 뒤 Free 가
     * 된다. 되돌리기 가장 비싼 동작이라 기본값이 꺼짐이어야 한다.
     */
    @Test
    fun `꺼져 있으면 갱신 대상을 조회조차 하지 않는다`() {
        runSchedulerWith(scheduler(renewalEnabled = false))

        verify(exactly = 0) { subscriptionRepository.findDueForBilling(any()) }
        verify(exactly = 0) { renewalService.renew(any(), any()) }
    }

    /** 갱신을 꺼도 체험 만료·유예·취소·다운그레이드는 그대로 돌아야 한다. */
    @Test
    fun `꺼져 있어도 기존 처리는 그대로 실행한다`() {
        runSchedulerWith(scheduler(renewalEnabled = false))

        verify(exactly = 1) { subscriptionRepository.findTrialExpired(any()) }
        verify(exactly = 1) { subscriptionRepository.findPausedToResume(any()) }
        verify(exactly = 1) { subscriptionRepository.findCancelledExpired(any()) }
        verify(exactly = 1) { subscriptionRepository.findWithPendingPlanType() }
        verify(atLeast = 1) { subscriptionRepository.findPastDue(any()) }
    }

    @Test
    fun `켜져 있으면 갱신을 실행한다`() {
        every { subscriptionRepository.findDueForBilling(any()) } returns listOf(subscription(1L))
        every { renewalService.renew(any(), any()) } returns SubscriptionRenewalOutcome.CHARGED

        runSchedulerWith(scheduler(renewalEnabled = true))

        verify(exactly = 1) { renewalService.renew(match { it.id == 1L }, any()) }
    }

    /**
     * 기본값이 켜짐이면 배포하는 순간 청구가 시작된다. 생성자 기본값이 아니라 설정
     * 표현식이 기본을 정하므로, 그 표현식을 고정한다.
     */
    @Test
    fun `설정 기본값이 꺼짐이다`() {
        val annotation = BillingScheduler::class.java.declaredConstructors
            .single()
            .parameters
            .mapNotNull { it.annotations.firstOrNull { a -> a.annotationClass.simpleName == "Value" } }
            .single() as org.springframework.beans.factory.annotation.Value

        assertEquals("\${subscription.renewal.enabled:false}", annotation.value)
    }

    private fun runSchedulerWith(target: BillingScheduler) {
        every { distributedLockPort.withLock(any(), any<() -> Unit>()) } answers {
            secondArg<() -> Unit>().invoke()
            true
        }
        every { subscriptionRepository.findTrialExpired(any()) } returns emptyList()
        every { subscriptionRepository.findPausedToResume(any()) } returns emptyList()
        every { subscriptionRepository.findPastDue(any()) } returns emptyList()
        every { subscriptionRepository.findCancelledExpired(any()) } returns emptyList()
        every { subscriptionRepository.findWithPendingPlanType() } returns emptyList()
        target.processBilling()
    }

    private fun runScheduler() {
        every { distributedLockPort.withLock(any(), any<() -> Unit>()) } answers {
            secondArg<() -> Unit>().invoke()
            true
        }
        // 갱신 외 블록은 전부 빈 목록으로 지나가게 둔다.
        every { subscriptionRepository.findTrialExpired(any()) } returns emptyList()
        every { subscriptionRepository.findPausedToResume(any()) } returns emptyList()
        every { subscriptionRepository.findPastDue(any()) } returns emptyList()
        every { subscriptionRepository.findCancelledExpired(any()) } returns emptyList()
        every { subscriptionRepository.findWithPendingPlanType() } returns emptyList()
        scheduler.processBilling()
    }

    @Test
    fun `갱신 대상이 없으면 갱신을 시도하지 않는다`() {
        every { subscriptionRepository.findDueForBilling(any()) } returns emptyList()

        runScheduler()

        verify(exactly = 1) { subscriptionRepository.findDueForBilling(any()) }
        verify(exactly = 0) { renewalService.renew(any(), any()) }
    }

    /** 이 호출이 없던 것이 P0 의 실체였다. */
    @Test
    fun `주기가 지난 구독을 조회해 건별로 갱신한다`() {
        every { subscriptionRepository.findDueForBilling(any()) } returns
            listOf(subscription(1L), subscription(2L))
        every { renewalService.renew(any(), any()) } returns SubscriptionRenewalOutcome.CHARGED

        runScheduler()

        verify(exactly = 1) { renewalService.renew(match { it.id == 1L }, any()) }
        verify(exactly = 1) { renewalService.renew(match { it.id == 2L }, any()) }
    }

    /**
     * 한 건이 터져도 나머지는 처리돼야 한다. 여기서 삼키지 않으면 첫 실패 구독이
     * 그날의 모든 갱신을 막는다.
     */
    @Test
    fun `한 건이 실패해도 나머지 구독을 계속 처리한다`() {
        every { subscriptionRepository.findDueForBilling(any()) } returns
            listOf(subscription(1L), subscription(2L), subscription(3L))
        val processed = mutableListOf<Long>()
        every { renewalService.renew(any(), any()) } answers {
            val sub = firstArg<Subscription>()
            if (sub.id == 2L) throw IllegalStateException("boom")
            processed += sub.id!!
            SubscriptionRenewalOutcome.CHARGED
        }

        runScheduler()

        assertEquals(listOf(1L, 3L), processed)
        verify(exactly = 3) { renewalService.renew(any(), any()) }
    }

    @Test
    fun `기간이 끝난 하향 예약은 옛 플랜으로 갱신하기 전에 적용한다`() {
        val order = mutableListOf<String>()
        val pending = subscription(4L).copy(
            planType = PlanType.BUSINESS,
            price = PlanType.BUSINESS.price,
            currentPeriodEnd = LocalDateTime.now().minusHours(1),
            nextBillingDate = LocalDateTime.now().minusHours(1),
            pendingPlanType = PlanType.STARTER,
        )
        var applied: Subscription? = null

        every { subscriptionRepository.findWithPendingPlanType() } answers {
            order += "downgrade-query"
            listOf(pending)
        }
        every { subscriptionRepository.update(any()) } answers {
            applied = firstArg()
            order += "downgrade-update"
            applied!!
        }
        every { subscriptionRepository.findDueForBilling(any()) } answers {
            order += "renewal-query"
            listOfNotNull(applied)
        }
        every { renewalService.renew(any(), any()) } answers {
            order += "renewal"
            SubscriptionRenewalOutcome.CHARGED
        }

        every { distributedLockPort.withLock(any(), any<() -> Unit>()) } answers {
            secondArg<() -> Unit>().invoke()
            true
        }
        every { subscriptionRepository.findTrialExpired(any()) } returns emptyList()
        every { subscriptionRepository.findPausedToResume(any()) } returns emptyList()
        every { subscriptionRepository.findPastDue(any()) } returns emptyList()
        every { subscriptionRepository.findCancelledExpired(any()) } returns emptyList()

        scheduler(renewalEnabled = true).processBilling()

        assertEquals(
            listOf("downgrade-query", "downgrade-update", "renewal-query", "renewal"),
            order.take(4),
        )
        verify(exactly = 1) {
            renewalService.renew(match {
                it.planType == PlanType.STARTER &&
                    it.price == PlanType.STARTER.price &&
                    it.pendingPlanType == null
            }, any())
        }
    }

    @Test
    fun `기간이 끝난 하향 예약은 선택한 결제 주기도 함께 적용한다`() {
        val due = LocalDateTime.now().minusHours(1)
        val pending = subscription(8L).copy(
            planType = PlanType.PRO,
            price = PlanType.PRO.price,
            currentPeriodEnd = due,
            nextBillingDate = due,
            pendingPlanType = PlanType.STARTER,
            pendingBillingCycle = BillingCycle.YEARLY,
        )
        var applied: Subscription? = null

        every { subscriptionRepository.findWithPendingPlanType() } returns listOf(pending)
        every { subscriptionRepository.update(any()) } answers {
            applied = firstArg()
            applied!!
        }
        every { subscriptionRepository.findDueForBilling(any()) } returns emptyList()
        every { subscriptionRepository.findTrialExpired(any()) } returns emptyList()
        every { subscriptionRepository.findPausedToResume(any()) } returns emptyList()
        every { subscriptionRepository.findPastDue(any()) } returns emptyList()
        every { subscriptionRepository.findCancelledExpired(any()) } returns emptyList()

        every { distributedLockPort.withLock(any(), any<() -> Unit>()) } answers {
            secondArg<() -> Unit>().invoke()
            true
        }

        scheduler(renewalEnabled = false).processBilling()

        verify {
            subscriptionRepository.update(match {
                it.planType == PlanType.STARTER &&
                    it.billingCycle == BillingCycle.YEARLY &&
                    it.price == PlanType.STARTER.yearlyPrice &&
                    it.pendingPlanType == null &&
                    it.pendingBillingCycle == null
            })
        }
    }

    @Test
    fun `해지·미납·일시정지 구독의 하향 예약은 활성화하거나 청구하지 않는다`() {
        val due = LocalDateTime.now().minusHours(1)
        val pendingSubscriptions = listOf(
            subscription(5L).copy(status = SubscriptionStatus.CANCELLED, currentPeriodEnd = due, nextBillingDate = due, pendingPlanType = PlanType.STARTER),
            subscription(6L).copy(status = SubscriptionStatus.PAST_DUE, currentPeriodEnd = due, nextBillingDate = due, pendingPlanType = PlanType.STARTER),
            subscription(7L).copy(status = SubscriptionStatus.PAUSED, currentPeriodEnd = due, nextBillingDate = due, pendingPlanType = PlanType.STARTER),
        )
        every { subscriptionRepository.findWithPendingPlanType() } returns pendingSubscriptions
        every { subscriptionRepository.findDueForBilling(any()) } returns emptyList()
        every { subscriptionRepository.findTrialExpired(any()) } returns emptyList()
        every { subscriptionRepository.findPausedToResume(any()) } returns emptyList()
        every { subscriptionRepository.findPastDue(any()) } returns emptyList()
        every { subscriptionRepository.findCancelledExpired(any()) } returns emptyList()
        every { distributedLockPort.withLock(any(), any<() -> Unit>()) } answers {
            secondArg<() -> Unit>().invoke()
            true
        }

        scheduler(renewalEnabled = true).processBilling()

        verify(exactly = 0) { renewalService.renew(any(), any()) }
        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }

    /**
     * 하향 적용은 갱신보다 먼저지만, 갱신은 여전히 유예 로직보다 먼저 돌아야 한다. 실패한
     * 갱신이 만든 PAST_DUE 를 같은 실행의 유예 블록이 볼 수 있어야, 3일 알림·7일 Free
     * 전환이 하루 늦지 않는다.
     */
    @Test
    fun `갱신을 유예 처리보다 먼저 실행한다`() {
        val order = mutableListOf<String>()
        every { subscriptionRepository.findDueForBilling(any()) } answers { order += "renewal"; emptyList() }
        every { distributedLockPort.withLock(any(), any<() -> Unit>()) } answers {
            secondArg<() -> Unit>().invoke()
            true
        }
        every { subscriptionRepository.findTrialExpired(any()) } answers { order += "trial"; emptyList() }
        every { subscriptionRepository.findPausedToResume(any()) } returns emptyList()
        every { subscriptionRepository.findPastDue(any()) } answers { order += "pastDue"; emptyList() }
        every { subscriptionRepository.findCancelledExpired(any()) } returns emptyList()
        every { subscriptionRepository.findWithPendingPlanType() } answers { order += "downgrade"; emptyList() }

        scheduler.processBilling()

        // findPastDue 는 3일·7일 두 번 불린다. 첫 등장 순서만 본다.
        assertEquals(listOf("downgrade", "renewal", "trial", "pastDue"), order.distinct())
    }
}
