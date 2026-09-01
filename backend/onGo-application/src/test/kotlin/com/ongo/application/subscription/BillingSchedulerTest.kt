package com.ongo.application.subscription

import com.ongo.common.enums.AuthProvider
import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.NotificationType
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.domain.credit.AiCredit
import com.ongo.domain.credit.CreditRepository
import com.ongo.domain.notification.Notification
import com.ongo.domain.notification.NotificationRepository
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.lock.DistributedLockPort
import com.ongo.domain.subscription.SubscriptionRenewalOutcome
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.User
import com.ongo.domain.user.UserRepository
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
class BillingSchedulerTest {

    @MockK
    private lateinit var subscriptionRepository: SubscriptionRepository

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var notificationRepository: NotificationRepository

    @MockK
    private lateinit var creditRepository: CreditRepository

    @MockK(relaxUnitFun = true)
    private lateinit var creditService: com.ongo.application.credit.CreditService

    @MockK
    private lateinit var distributedLockPort: DistributedLockPort

    @MockK
    private lateinit var renewalService: SubscriptionRenewalService

    private lateinit var billingScheduler: BillingScheduler

    private val now = LocalDateTime.now()

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        // withLock 은 블록을 그대로 실행하고 true 를 돌려준다(락 획득 성공).
        every { distributedLockPort.withLock(any(), any<() -> Unit>()) } answers {
            secondArg<() -> Unit>().invoke()
            true
        }
        // 갱신 대상이 없는 것이 이 테스트들의 기본 전제다. 갱신 경로는
        // SubscriptionRenewalServiceTest / BillingSchedulerRenewalTest 가 따로 본다.
        every { subscriptionRepository.findDueForBilling(any()) } returns emptyList()
        billingScheduler = BillingScheduler(
            subscriptionRepository,
            userRepository,
            notificationRepository,
            creditRepository,
            distributedLockPort,
            creditService,
            renewalService,
            // 건별 트랜잭션은 콜백을 그대로 실행한다. 트랜잭션 경계 자체는 통합 테스트 몫이다.
            DummyTransactionManagerForTest(),
            // 이 파일은 갱신이 아닌 기존 처리(체험·유예·취소·다운그레이드)를 본다.
            // 갱신 토글 계약은 BillingSchedulerRenewalTest 가 따로 고정한다.
            renewalEnabled = false,
        )
    }

    private fun createUser(id: Long, planType: PlanType = PlanType.STARTER): User = User(
        id = id,
        email = "user$id@test.com",
        name = "TestUser$id",
        provider = AuthProvider.GOOGLE,
        providerId = "google_$id",
        planType = planType,
    )

    private fun createSubscription(
        id: Long = 1L,
        userId: Long = 1L,
        planType: PlanType = PlanType.STARTER,
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        billingCycle: BillingCycle = BillingCycle.MONTHLY,
        currentPeriodEnd: LocalDateTime? = null,
        /**
         * 기본값 `null` 은 종전 그대로다 — 다른 테스트의 하향 예약 경계 판정
         * (`currentPeriodEnd ?: nextBillingDate`)을 바꾸지 않기 위해 건드리지 않는다.
         *
         * **일시정지된 유료 구독 픽스처는 이 값을 반드시 채워야 한다.** 실제 유료 구독은
         * 청구창 없이 PAUSED 가 되지 않고, 재개 경로가 그 사실을 검사하기 때문이다
         * (`Subscription.missingPaidBillingWindow`).
         */
        nextBillingDate: LocalDateTime? = null,
        pendingPlanType: PlanType? = null,
        pendingBillingCycle: BillingCycle? = null,
        pausedAt: LocalDateTime? = null,
        resumeAt: LocalDateTime? = null,
    ): Subscription = Subscription(
        id = id,
        userId = userId,
        planType = planType,
        status = status,
        price = planType.price,
        billingCycle = billingCycle,
        currentPeriodStart = now.minusDays(30),
        currentPeriodEnd = currentPeriodEnd,
        nextBillingDate = nextBillingDate,
        pendingPlanType = pendingPlanType,
        pendingBillingCycle = pendingBillingCycle,
        pausedAt = pausedAt,
        resumeAt = resumeAt,
    )

    private fun stubEmptyDefaults() {
        every { subscriptionRepository.findTrialExpired(any()) } returns emptyList()
        every { subscriptionRepository.findPausedToResume(any()) } returns emptyList()
        every { subscriptionRepository.findPastDue(3) } returns emptyList()
        every { subscriptionRepository.findPastDue(7) } returns emptyList()
        every { subscriptionRepository.findDueForBilling(any()) } returns emptyList()
        every { subscriptionRepository.findCancelledExpired(any()) } returns emptyList()
        every { subscriptionRepository.findWithPendingPlanType() } returns emptyList()
    }

    /**
     * 체험 만료 한 건이 실패해도 나머지가 계속 처리되는지.
     *
     * 예전에는 다섯 블록이 한 트랜잭션이라 한 명의 크레딧 갱신 실패가 그날 밤 배치를
     * 통째로 멈췄다 — 나머지 만료·일시정지 재개·유예 알림·Free 전환이 전부 미뤄지고
     * 유료 권한이 하루 더 유지됐다.
     */
    @Test
    @DisplayName("체험 만료 한 건이 실패해도 나머지 건은 계속 처리한다")
    fun `체험 만료 - 건별 격리`() {
        stubEmptyDefaults()
        val failing = createSubscription(id = 1L, userId = 11L, status = SubscriptionStatus.TRIALING)
        val healthy = createSubscription(id = 2L, userId = 22L, status = SubscriptionStatus.TRIALING)
        every { subscriptionRepository.findTrialExpired(any()) } returns listOf(failing, healthy)
        every { userRepository.findById(any()) } answers { createUser(firstArg()) }
        every { userRepository.update(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        // 첫 건만 크레딧 회수에서 터진다.
        every {
            creditService.applyPlanEntitlement(11L, PlanType.FREE, "TRIAL_EXPIRED")
        } throws IllegalStateException("크레딧 갱신 실패")
        every {
            creditService.applyPlanEntitlement(22L, PlanType.FREE, "TRIAL_EXPIRED")
        } returns Unit

        billingScheduler.processBilling()

        // 실패한 건 때문에 배치가 멈추지 않는다.
        verify(exactly = 1) { creditService.applyPlanEntitlement(22L, PlanType.FREE, "TRIAL_EXPIRED") }
        // 정상 건은 상태 전환과 알림까지 끝난다.
        verify {
            subscriptionRepository.update(match { it.userId == 22L && it.planType == PlanType.FREE })
        }
        verify { notificationRepository.save(match { it.userId == 22L && it.title == "트라이얼 만료" }) }
    }

    /** 뒤 블록들도 계속 돌아야 한다 — 체험 실패가 유예·Free 전환을 막으면 안 된다. */
    @Test
    @DisplayName("체험 만료가 실패해도 뒤따르는 처리 블록이 실행된다")
    fun `체험 만료 실패 - 후속 블록 계속`() {
        stubEmptyDefaults()
        val failing = createSubscription(id = 1L, userId = 11L, status = SubscriptionStatus.TRIALING)
        val paused = createSubscription(
            id = 3L,
            userId = 33L,
            status = SubscriptionStatus.PAUSED,
            // 일시정지된 유료 구독은 청구창을 그대로 들고 있다. 재개가 그것을 요구한다.
            currentPeriodEnd = now.plusDays(30),
            nextBillingDate = now.plusDays(30),
            pausedAt = now.minusDays(31),
            resumeAt = now.minusDays(1),
        )
        every { subscriptionRepository.findTrialExpired(any()) } returns listOf(failing)
        every { subscriptionRepository.findPausedToResume(any()) } returns listOf(paused)
        every { userRepository.findById(any()) } answers { createUser(firstArg()) }
        every { userRepository.update(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every {
            creditService.applyPlanEntitlement(11L, PlanType.FREE, "TRIAL_EXPIRED")
        } throws IllegalStateException("크레딧 갱신 실패")

        billingScheduler.processBilling()

        verify {
            subscriptionRepository.update(
                match { it.userId == 33L && it.status == SubscriptionStatus.ACTIVE },
            )
        }
        verify { notificationRepository.save(match { it.userId == 33L && it.title == "구독 자동 재개" }) }
    }

    /** 실패를 삼키되 운영자가 찾을 수 있어야 한다. userId 가 없으면 추적이 불가능하다. */
    @Test
    @DisplayName("실패한 체험 만료 건은 userId 와 예외를 남긴다")
    fun `체험 만료 실패 - 구조화 로그`() {
        stubEmptyDefaults()
        val failing = createSubscription(id = 77L, userId = 88L, status = SubscriptionStatus.TRIALING)
        every { subscriptionRepository.findTrialExpired(any()) } returns listOf(failing)
        every { userRepository.findById(any()) } answers { createUser(firstArg()) }
        every { userRepository.update(any()) } answers { firstArg() }
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every {
            creditService.applyPlanEntitlement(88L, PlanType.FREE, "TRIAL_EXPIRED")
        } throws IllegalStateException("크레딧 갱신 실패")

        val logger = LoggerFactory.getLogger(BillingScheduler::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            billingScheduler.processBilling()
        } finally {
            logger.detachAppender(appender)
        }

        val failure = appender.list.single { it.level == Level.ERROR }
        // 어느 블록에서 났는지(action) + 어느 건인지(subscriptionId, userId) + 원인.
        assertEquals(listOf("트라이얼 만료", 77L, 88L), failure.argumentArray?.toList())
        assertEquals("크레딧 갱신 실패", failure.throwableProxy?.message)
    }

    /**
     * 하향 적용은 **가장 먼저** 실행된다. 여기서 한 건이 터지면 남은 하향 예약뿐 아니라
     * 자동 갱신과 체험 만료·유예·취소 처리까지 그날 전부 멈춘다. 그러면 "기간 경계에서
     * 옛 플랜으로 청구하지 않으려고" 이 블록을 앞에 둔 의도가 뒤집힌다.
     */
    @Test
    @DisplayName("하향 적용 한 건이 실패해도 나머지 건과 갱신·후속 처리가 계속된다")
    fun `하향 적용 - 건별 격리`() {
        stubEmptyDefaults()
        val failing = createSubscription(
            id = 1L, userId = 11L, planType = PlanType.PRO,
            currentPeriodEnd = now.minusDays(1), pendingPlanType = PlanType.STARTER,
        )
        val healthy = createSubscription(
            id = 2L, userId = 22L, planType = PlanType.PRO,
            currentPeriodEnd = now.minusDays(1), pendingPlanType = PlanType.FREE,
        )
        every { subscriptionRepository.findWithPendingPlanType() } returns listOf(failing, healthy)
        every { subscriptionRepository.findPastDue(3) } returns listOf(
            createSubscription(id = 3L, userId = 33L, status = SubscriptionStatus.PAST_DUE),
        )
        every { userRepository.findById(any()) } answers { createUser(firstArg()) }
        every { userRepository.update(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every {
            creditService.applyPlanEntitlement(11L, PlanType.STARTER, "PENDING_PLAN_CHANGE")
        } throws IllegalStateException("크레딧 반영 실패")
        every {
            creditService.applyPlanEntitlement(22L, PlanType.FREE, "PENDING_PLAN_CHANGE")
        } returns Unit

        billingScheduler.processBilling()

        // 같은 블록의 정상 건이 계속된다.
        verify(exactly = 1) { creditService.applyPlanEntitlement(22L, PlanType.FREE, "PENDING_PLAN_CHANGE") }
        // 뒤따르는 단계(기존 처리)도 실행된다.
        verify { notificationRepository.save(match { it.userId == 33L && it.title == "결제 실패 알림" }) }
    }

    /** 하향 실패가 자동 갱신까지 막으면 청구가 하루 밀린다. */
    @Test
    @DisplayName("하향 적용이 실패해도 자동 갱신 단계가 실행된다")
    fun `하향 적용 실패 - 갱신 계속`() {
        stubEmptyDefaults()
        val failing = createSubscription(
            id = 1L, userId = 11L, planType = PlanType.PRO,
            currentPeriodEnd = now.minusDays(1), pendingPlanType = PlanType.STARTER,
        )
        val due = createSubscription(id = 9L, userId = 99L, currentPeriodEnd = now.minusDays(1))
        every { subscriptionRepository.findWithPendingPlanType() } returns listOf(failing)
        every { subscriptionRepository.findDueForBilling(any()) } returns listOf(due)
        every { userRepository.findById(any()) } answers { createUser(firstArg()) }
        every { userRepository.update(any()) } answers { firstArg() }
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }
        every {
            creditService.applyPlanEntitlement(11L, PlanType.STARTER, "PENDING_PLAN_CHANGE")
        } throws IllegalStateException("크레딧 반영 실패")
        every { renewalService.renew(any(), any()) } returns SubscriptionRenewalOutcome.CHARGED

        // 갱신이 켜진 스케줄러로 확인한다. 기본 인스턴스는 토글이 꺼져 있다.
        BillingScheduler(
            subscriptionRepository, userRepository, notificationRepository, creditRepository,
            distributedLockPort, creditService, renewalService,
            DummyTransactionManagerForTest(), renewalEnabled = true,
        ).processBilling()

        verify(exactly = 1) { renewalService.renew(match { it.id == 9L }, any()) }
    }

    /**
     * **조회 자체가 깨지는 경우.**
     *
     * 건별 격리는 목록을 받은 뒤에만 작동한다. 목록 조회가 터지면 그 예외는 여전히 위로
     * 올라와 뒤 단계를 전부 막는다 — 단계 단위 격리가 필요한 이유다.
     */
    @Test
    @DisplayName("한 단계의 조회가 실패해도 다음 단계는 실행된다")
    fun `단계 격리 - 조회 실패`() {
        stubEmptyDefaults()
        every { subscriptionRepository.findWithPendingPlanType() } throws
            IllegalStateException("조회 실패")
        every { subscriptionRepository.findPastDue(3) } returns listOf(
            createSubscription(id = 3L, userId = 33L, status = SubscriptionStatus.PAST_DUE),
        )
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }

        billingScheduler.processBilling()

        // 하향 단계가 통째로 깨져도 기존 처리 단계는 돈다.
        verify { notificationRepository.save(match { it.userId == 33L && it.title == "결제 실패 알림" }) }
    }

    /** 예약 플랜과 주기는 함께 반영되고 함께 지워져야 한다. */
    @Test
    @DisplayName("예약 플랜과 결제 주기를 한 덩어리로 적용한다")
    fun `하향 적용 - 플랜과 주기 원자성`() {
        stubEmptyDefaults()
        every { subscriptionRepository.findWithPendingPlanType() } returns listOf(
            createSubscription(
                id = 1L, userId = 11L, planType = PlanType.PRO, billingCycle = BillingCycle.MONTHLY,
                currentPeriodEnd = now.minusDays(1),
                pendingPlanType = PlanType.STARTER, pendingBillingCycle = BillingCycle.YEARLY,
            ),
        )
        every { userRepository.findById(any()) } answers { createUser(firstArg()) }
        every { userRepository.update(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }
        val saved = slot<Subscription>()
        every { subscriptionRepository.update(capture(saved)) } answers { firstArg() }

        billingScheduler.processBilling()

        val result = saved.captured
        assertEquals(PlanType.STARTER, result.planType)
        assertEquals(BillingCycle.YEARLY, result.billingCycle)
        // 가격은 반영된 주기 기준이어야 한다. 어긋나면 다음 청구가 1/12 만 걷힌다.
        assertEquals(PlanType.STARTER.priceFor(BillingCycle.YEARLY), result.price)
        assertNull(result.pendingPlanType)
        assertNull(result.pendingBillingCycle)
    }

    /* ── 하향 예약 경계: 기간을 모르는 행 ─────────────────────────── */

    /**
     * **기간을 모르면 만료된 것으로 본다.**
     *
     * `current_period_end`·`next_billing_date` 는 둘 다 NOT NULL 이 아니다. 예전에는 둘 다
     * 비어 있으면 경계 판정이 false 가 되어 그 예약이 **영원히 적용되지 않았고 로그도 남지
     * 않았다.** 침묵의 결과는 한쪽뿐이다 — 하향을 요청한 사용자가 상위 플랜에 무기한 남는다.
     *
     * `changePlan` 은 그때 `effectiveDate = currentPeriodEnd ?: now` 로 **"지금 적용된다"**
     * 고 응답한다. 응답과 실제가 어긋나면 안 된다.
     *
     * 정책은 `findCancelledExpired` 가 이미 문서화했다 — 알 수 없는 기간은 만료로 본다.
     */
    @Test
    @DisplayName("기간 정보가 전혀 없는 하향 예약도 적용한다")
    fun `하향 적용 - 기간 미상은 즉시 적용`() {
        stubEmptyDefaults()
        every { subscriptionRepository.findWithPendingPlanType() } returns listOf(
            createSubscription(
                id = 1L, userId = 11L, planType = PlanType.PRO,
                currentPeriodEnd = null, nextBillingDate = null,
                pendingPlanType = PlanType.FREE,
            ),
        )
        every { userRepository.findById(any()) } answers { createUser(firstArg()) }
        every { userRepository.update(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }
        val saved = slot<Subscription>()
        every { subscriptionRepository.update(capture(saved)) } answers { firstArg() }

        billingScheduler.processBilling()

        assertEquals(PlanType.FREE, saved.captured.planType)
        assertNull(saved.captured.pendingPlanType)
        verify { creditService.applyPlanEntitlement(11L, PlanType.FREE, "PENDING_PLAN_CHANGE") }
    }

    /**
     * **이미 받은 요금만큼은 지킨다.** 기간이 남아 있으면 적용하지 않는다 — 이것이 예약의
     * 존재 이유이고, 앞의 "기간 미상" 처리가 이 계약을 삼키면 안 된다.
     */
    @Test
    @DisplayName("결제 기간이 남아 있으면 하향을 적용하지 않는다")
    fun `하향 적용 - 남은 기간 보호`() {
        stubEmptyDefaults()
        every { subscriptionRepository.findWithPendingPlanType() } returns listOf(
            createSubscription(
                id = 1L, userId = 11L, planType = PlanType.PRO,
                currentPeriodEnd = now.plusDays(10),
                pendingPlanType = PlanType.FREE,
            ),
        )

        billingScheduler.processBilling()

        verify(exactly = 0) { subscriptionRepository.update(any()) }
        verify(exactly = 0) { creditService.applyPlanEntitlement(any(), any(), any()) }
    }

    /** `currentPeriodEnd` 가 없어도 `nextBillingDate` 가 남아 있으면 그 경계를 지킨다. */
    @Test
    @DisplayName("기간 종료일이 없어도 다음 청구일이 남아 있으면 지킨다")
    fun `하향 적용 - 다음 청구일 경계 보호`() {
        stubEmptyDefaults()
        every { subscriptionRepository.findWithPendingPlanType() } returns listOf(
            createSubscription(
                id = 1L, userId = 11L, planType = PlanType.PRO,
                currentPeriodEnd = null, nextBillingDate = now.plusDays(3),
                pendingPlanType = PlanType.FREE,
            ),
        )

        billingScheduler.processBilling()

        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }

    /** 주기만 남은 행은 적용하지 않고 경고만 남긴다 — 기존 의미 보존. */
    @Test
    @DisplayName("예약 플랜 없이 주기만 있는 행은 건드리지 않는다")
    fun `하향 적용 - 주기만 있는 행 경고`() {
        stubEmptyDefaults()
        every { subscriptionRepository.findWithPendingPlanType() } returns listOf(
            createSubscription(
                id = 7L, userId = 77L, currentPeriodEnd = now.minusDays(1),
                pendingPlanType = null, pendingBillingCycle = BillingCycle.YEARLY,
            ),
        )

        billingScheduler.processBilling()

        verify(exactly = 0) { subscriptionRepository.update(any()) }
        verify(exactly = 0) { creditService.applyPlanEntitlement(any(), any(), any()) }
    }

    /**
     * 나머지 네 블록도 건별로 격리돼야 한다.
     *
     * 체험만 격리하면 "체험 실패가 전체를 막는" 경로만 닫힌다. 예컨대 일시정지 재개 한
     * 건이 실패하면 여전히 뒤의 유예 알림·Free 전환이 통째로 멈춘다.
     */
    @Test
    @DisplayName("일시정지 재개 한 건이 실패해도 나머지 건과 후속 블록이 계속된다")
    fun `일시정지 재개 - 건별 격리`() {
        stubEmptyDefaults()
        // 둘 다 청구창이 온전한 정상 일시정지 건이다. 여기서 재려는 것은 격리이므로,
        // 재개가 거부되는 다른 이유가 섞이면 안 된다.
        val failing = createSubscription(
            id = 1L, userId = 11L, status = SubscriptionStatus.PAUSED,
            currentPeriodEnd = now.plusDays(30), nextBillingDate = now.plusDays(30),
            pausedAt = now.minusDays(31), resumeAt = now.minusDays(1),
        )
        val healthy = createSubscription(
            id = 2L, userId = 22L, status = SubscriptionStatus.PAUSED,
            currentPeriodEnd = now.plusDays(30), nextBillingDate = now.plusDays(30),
            pausedAt = now.minusDays(31), resumeAt = now.minusDays(1),
        )
        val pastDue = createSubscription(id = 3L, userId = 33L, status = SubscriptionStatus.PAST_DUE)
        every { subscriptionRepository.findPausedToResume(any()) } returns listOf(failing, healthy)
        every { subscriptionRepository.findPastDue(3) } returns listOf(pastDue)
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers {
            val notification = firstArg<Notification>()
            if (notification.userId == 11L) throw IllegalStateException("알림 저장 실패")
            notification
        }

        billingScheduler.processBilling()

        // 같은 블록의 정상 건이 계속된다.
        verify { subscriptionRepository.update(match { it.userId == 22L && it.status == SubscriptionStatus.ACTIVE }) }
        // 뒤따르는 블록도 실행된다.
        verify { notificationRepository.save(match { it.userId == 33L && it.title == "결제 실패 알림" }) }
    }

    @Test
    @DisplayName("미결제 7일 Free 전환 한 건이 실패해도 나머지 건이 계속된다")
    fun `미결제 7일 - 건별 격리`() {
        stubEmptyDefaults()
        val failing = createSubscription(id = 1L, userId = 11L, status = SubscriptionStatus.PAST_DUE)
        val healthy = createSubscription(id = 2L, userId = 22L, status = SubscriptionStatus.PAST_DUE)
        every { subscriptionRepository.findPastDue(7) } returns listOf(failing, healthy)
        every { userRepository.findById(any()) } answers { createUser(firstArg()) }
        every { userRepository.update(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { creditService.applyPlanEntitlement(11L, PlanType.FREE, "PAST_DUE") } throws
            IllegalStateException("크레딧 회수 실패")
        every { creditService.applyPlanEntitlement(22L, PlanType.FREE, "PAST_DUE") } returns Unit

        billingScheduler.processBilling()

        verify(exactly = 1) { creditService.applyPlanEntitlement(22L, PlanType.FREE, "PAST_DUE") }
        verify { notificationRepository.save(match { it.userId == 22L && it.title == "구독 만료" }) }
    }

    @Test
    @DisplayName("취소 만료 Free 전환 한 건이 실패해도 나머지 건이 계속된다")
    fun `취소 만료 - 건별 격리`() {
        stubEmptyDefaults()
        val failing = createSubscription(id = 1L, userId = 11L, status = SubscriptionStatus.CANCELLED)
        val healthy = createSubscription(id = 2L, userId = 22L, status = SubscriptionStatus.CANCELLED)
        every { subscriptionRepository.findCancelledExpired(any()) } returns listOf(failing, healthy)
        every { userRepository.findById(any()) } answers { createUser(firstArg()) }
        every { userRepository.update(any()) } answers { firstArg() }
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every {
            creditService.applyPlanEntitlement(11L, PlanType.FREE, "SUBSCRIPTION_CANCELLED")
        } throws IllegalStateException("크레딧 회수 실패")
        every {
            creditService.applyPlanEntitlement(22L, PlanType.FREE, "SUBSCRIPTION_CANCELLED")
        } returns Unit

        billingScheduler.processBilling()

        verify(exactly = 1) {
            creditService.applyPlanEntitlement(22L, PlanType.FREE, "SUBSCRIPTION_CANCELLED")
        }
    }

    /**
     * **건별 처리가 실제로 REQUIRES_NEW 경계를 지나는지.**
     *
     * `runCatching` 만 남기고 `perItemTx.execute` 를 지워도 다른 테스트는 전부 통과한다 —
     * DummyTransactionManagerForTest 가 콜백을 그대로 실행하기 때문이다. 그러면 예외를
     * 삼켜도 `applyPlanEntitlement`(@Transactional REQUIRED)가 바깥을 rollbackOnly 로
     * 만들어 배치 전체가 되돌아가는, 가짜 격리로 조용히 되돌아간다. 여기서 그 회귀를 막는다.
     */
    @Test
    @DisplayName("건별 처리는 새 트랜잭션 경계를 지난다")
    fun `건별 처리 - REQUIRES_NEW 경계 통과`() {
        val propagations = mutableListOf<Int>()
        val recordingManager = object : PlatformTransactionManager {
            override fun getTransaction(definition: TransactionDefinition?): TransactionStatus {
                definition?.let { propagations.add(it.propagationBehavior) }
                return SimpleTransactionStatus()
            }

            override fun commit(status: TransactionStatus) = Unit
            override fun rollback(status: TransactionStatus) = Unit
        }
        val scheduler = BillingScheduler(
            subscriptionRepository, userRepository, notificationRepository, creditRepository,
            distributedLockPort, creditService, renewalService, recordingManager,
            renewalEnabled = false,
        )
        stubEmptyDefaults()
        every { subscriptionRepository.findPausedToResume(any()) } returns listOf(
            createSubscription(
                id = 1L, userId = 11L, status = SubscriptionStatus.PAUSED,
                currentPeriodEnd = now.plusDays(30), nextBillingDate = now.plusDays(30),
            ),
        )
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }

        scheduler.processBilling()

        assertTrue(
            propagations.contains(TransactionDefinition.PROPAGATION_REQUIRES_NEW),
            "건별 처리가 REQUIRES_NEW 경계를 지나지 않았습니다: $propagations",
        )
    }

    /** 실패한 블록 이름이 로그에 있어야 운영자가 어디서 났는지 안다. */
    @Test
    @DisplayName("실패 로그에 어느 블록인지가 남는다")
    fun `건별 실패 - 블록 이름 로그`() {
        stubEmptyDefaults()
        val failing = createSubscription(id = 5L, userId = 55L, status = SubscriptionStatus.CANCELLED)
        every { subscriptionRepository.findCancelledExpired(any()) } returns listOf(failing)
        every { userRepository.findById(any()) } answers { createUser(firstArg()) }
        every { userRepository.update(any()) } answers { firstArg() }
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every {
            creditService.applyPlanEntitlement(55L, PlanType.FREE, "SUBSCRIPTION_CANCELLED")
        } throws IllegalStateException("크레딧 회수 실패")

        val logger = LoggerFactory.getLogger(BillingScheduler::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            billingScheduler.processBilling()
        } finally {
            logger.detachAppender(appender)
        }

        val failure = appender.list.single { it.level == Level.ERROR }
        assertEquals(listOf("취소 만료 Free 전환", 5L, 55L), failure.argumentArray?.toList())
    }

    @Test
    @DisplayName("트라이얼 만료 시 Free 플랜으로 전환되어야 한다")
    fun `트라이얼 만료 - Free 전환`() {
        // given
        val sub = createSubscription(
            id = 1L,
            userId = 1L,
            planType = PlanType.PRO,
            status = SubscriptionStatus.TRIALING,
        )
        val user = createUser(1L, PlanType.PRO)

        every { distributedLockPort.withLock(any(), any<() -> Unit>()) } answers {
            secondArg<() -> Unit>().invoke()
            true
        }
        stubEmptyDefaults()
        every { subscriptionRepository.findTrialExpired(any()) } returns listOf(sub)
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { userRepository.findById(1L) } returns user
        every { userRepository.update(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }

        // when
        billingScheduler.processBilling()

        // then
        val subSlot = slot<Subscription>()
        verify { subscriptionRepository.update(capture(subSlot)) }
        assertEquals(PlanType.FREE, subSlot.captured.planType)
        assertEquals(SubscriptionStatus.FREE, subSlot.captured.status)
        assertEquals(0, subSlot.captured.price)

        val userSlot = slot<User>()
        verify { userRepository.update(capture(userSlot)) }
        assertEquals(PlanType.FREE, userSlot.captured.planType)

        // 체험으로 받은 유료 크레딧이 만료 후에도 남으면 권한이 새어나간다.
        verify(exactly = 1) {
            creditService.applyPlanEntitlement(1L, PlanType.FREE, "TRIAL_EXPIRED")
        }

        val notifSlot = slot<Notification>()
        verify { notificationRepository.save(capture(notifSlot)) }
        assertEquals(1L, notifSlot.captured.userId)
        assertEquals(NotificationType.SYSTEM, notifSlot.captured.type)
        assertEquals("트라이얼 만료", notifSlot.captured.title)
    }

    @Test
    @DisplayName("일시정지 30일 초과 시 자동으로 구독이 재개되어야 한다")
    fun `일시정지 자동 재개`() {
        // given
        val sub = createSubscription(
            id = 2L,
            userId = 2L,
            planType = PlanType.STARTER,
            status = SubscriptionStatus.PAUSED,
            // 일시정지된 유료 구독은 청구창을 그대로 들고 있다. 재개가 그것을 요구한다.
            currentPeriodEnd = now.plusDays(30),
            nextBillingDate = now.plusDays(30),
            pausedAt = now.minusDays(31),
            resumeAt = now.minusDays(1),
        )

        stubEmptyDefaults()
        every { subscriptionRepository.findPausedToResume(any()) } returns listOf(sub)
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }

        // when
        billingScheduler.processBilling()

        // then
        val subSlot = slot<Subscription>()
        verify { subscriptionRepository.update(capture(subSlot)) }
        assertEquals(SubscriptionStatus.ACTIVE, subSlot.captured.status)
        assertNull(subSlot.captured.pausedAt)
        assertNull(subSlot.captured.resumeAt)

        val notifSlot = slot<Notification>()
        verify { notificationRepository.save(capture(notifSlot)) }
        assertEquals(2L, notifSlot.captured.userId)
        assertEquals("구독 자동 재개", notifSlot.captured.title)
    }

    /**
     * **청구창이 없는 유료 구독은 재개하지 않는다.**
     *
     * 재개는 결제를 거치지 않고 상태만 되돌린다. 기간이 빈 채로 ACTIVE 가 되면 그 구독은
     * `findDueForBilling`(`next_billing_date <= now`) 에도 `findTrialExpired` 에도 걸리지
     * 않아(NULL 비교가 UNKNOWN) 결제 없이 유료 권한이 무기한 유지된다. 운영에서 실제로
     * 그런 구독이 발견됐다.
     *
     * 예외는 `forEachIsolated` 가 건별로 잡으므로 이 건만 건너뛰고 뒤 블록은 계속 간다.
     */
    @Test
    @DisplayName("청구 기간이 없는 유료 구독은 재개하지 않고 이 건만 건너뛴다")
    fun `일시정지 재개 - 기간 없는 유료 구독 차단`() {
        stubEmptyDefaults()
        val broken = createSubscription(
            id = 7L,
            userId = 77L,
            planType = PlanType.BUSINESS,
            status = SubscriptionStatus.PAUSED,
            // currentPeriodEnd / nextBillingDate 를 채우지 않는다 — 이것이 재현 대상이다.
            pausedAt = now.minusDays(31),
            resumeAt = now.minusDays(1),
        )
        val pastDue = createSubscription(id = 8L, userId = 88L, status = SubscriptionStatus.PAST_DUE)
        every { subscriptionRepository.findPausedToResume(any()) } returns listOf(broken)
        every { subscriptionRepository.findPastDue(3) } returns listOf(pastDue)
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }

        billingScheduler.processBilling()

        verify(exactly = 0) {
            subscriptionRepository.update(match { it.userId == 77L && it.status == SubscriptionStatus.ACTIVE })
        }
        verify(exactly = 0) { notificationRepository.save(match { it.userId == 77L }) }
        // 뒤따르는 블록은 계속 간다 — 차단이 배치 전체를 멈추지 않는다.
        verify { notificationRepository.save(match { it.userId == 88L && it.title == "결제 실패 알림" }) }
    }

    /**
     * **FREE 구독은 종전 그대로 재개된다.**
     *
     * 무료 구독에는 청구창이 없다. 같은 조건을 걸면 무료 사용자가 영영 PAUSED 로 남는다.
     */
    @Test
    @DisplayName("FREE 구독은 기간이 없어도 종전대로 재개된다")
    fun `일시정지 재개 - FREE 는 기간 없이도 재개된다`() {
        stubEmptyDefaults()
        val free = createSubscription(
            id = 9L,
            userId = 99L,
            planType = PlanType.FREE,
            status = SubscriptionStatus.PAUSED,
            pausedAt = now.minusDays(31),
            resumeAt = now.minusDays(1),
        )
        every { subscriptionRepository.findPausedToResume(any()) } returns listOf(free)
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }

        billingScheduler.processBilling()

        verify {
            subscriptionRepository.update(match { it.userId == 99L && it.status == SubscriptionStatus.ACTIVE })
        }
        verify { notificationRepository.save(match { it.userId == 99L && it.title == "구독 자동 재개" }) }
    }

    @Test
    @DisplayName("미결제 3일 유예 시 알림이 발송되고 PAST_DUE 상태로 변경되어야 한다")
    fun `미결제 3일 알림`() {
        // given
        val sub = createSubscription(
            id = 3L,
            userId = 3L,
            planType = PlanType.PRO,
            status = SubscriptionStatus.ACTIVE,
        )

        stubEmptyDefaults()
        every { subscriptionRepository.findPastDue(3) } returns listOf(sub)
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }

        // when
        billingScheduler.processBilling()

        // then
        val notifSlot = slot<Notification>()
        verify { notificationRepository.save(capture(notifSlot)) }
        assertEquals(3L, notifSlot.captured.userId)
        assertEquals("결제 실패 알림", notifSlot.captured.title)

        val subSlot = slot<Subscription>()
        verify { subscriptionRepository.update(capture(subSlot)) }
        assertEquals(SubscriptionStatus.PAST_DUE, subSlot.captured.status)
    }

    @Test
    @DisplayName("미결제 7일 경과 시 Free 플랜으로 전환되어야 한다")
    fun `미결제 7일 Free 전환`() {
        // given
        val sub = createSubscription(
            id = 4L,
            userId = 4L,
            planType = PlanType.BUSINESS,
            status = SubscriptionStatus.PAST_DUE,
        )
        val user = createUser(4L, PlanType.BUSINESS)

        stubEmptyDefaults()
        every { subscriptionRepository.findPastDue(7) } returns listOf(sub)
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { userRepository.findById(4L) } returns user
        every { userRepository.update(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }

        // when
        billingScheduler.processBilling()

        // then
        val subSlot = slot<Subscription>()
        verify { subscriptionRepository.update(capture(subSlot)) }
        assertEquals(PlanType.FREE, subSlot.captured.planType)
        assertEquals(SubscriptionStatus.FREE, subSlot.captured.status)
        assertEquals(0, subSlot.captured.price)

        val userSlot = slot<User>()
        verify { userRepository.update(capture(userSlot)) }
        assertEquals(PlanType.FREE, userSlot.captured.planType)

        val notifSlot = slot<Notification>()
        verify { notificationRepository.save(capture(notifSlot)) }
        assertEquals(4L, notifSlot.captured.userId)
        assertEquals("구독 만료", notifSlot.captured.title)
    }

    @Test
    @DisplayName("취소된 구독의 기간이 만료되면 Free 플랜으로 전환되어야 한다")
    fun `취소 구독 기간 만료 - Free 전환`() {
        // given
        val sub = createSubscription(
            id = 5L,
            userId = 5L,
            planType = PlanType.PRO,
            status = SubscriptionStatus.CANCELLED,
            currentPeriodEnd = now.minusDays(1), // 기간 만료됨
        )
        val user = createUser(5L, PlanType.PRO)

        stubEmptyDefaults()
        // 취소 만료 대상은 전용 쿼리로 조회한다. 예전에는 findDueForBilling(status='ACTIVE')
        // 이 CANCELLED 구독을 반환하도록 mock 해서, 현실에서 일어날 수 없는 상황으로
        // 죽은 코드를 통과시키고 있었다.
        every { subscriptionRepository.findCancelledExpired(any()) } returns listOf(sub)
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { userRepository.findById(5L) } returns user
        every { userRepository.update(any()) } answers { firstArg() }

        // when
        billingScheduler.processBilling()

        // then
        val subSlot = slot<Subscription>()
        verify { subscriptionRepository.update(capture(subSlot)) }
        assertEquals(PlanType.FREE, subSlot.captured.planType)
        assertEquals(SubscriptionStatus.FREE, subSlot.captured.status)
        assertEquals(0, subSlot.captured.price)

        val userSlot = slot<User>()
        verify { userRepository.update(capture(userSlot)) }
        assertEquals(PlanType.FREE, userSlot.captured.planType)
    }

    @Test
    @DisplayName("취소 구독이지만 기간이 남아있으면 Free 전환하지 않아야 한다")
    fun `취소 구독 기간 미만료 - 변환 안 함`() {
        // given
        val sub = createSubscription(
            id = 6L,
            userId = 6L,
            planType = PlanType.PRO,
            status = SubscriptionStatus.CANCELLED,
            currentPeriodEnd = now.plusDays(10), // 아직 기간 남음
        )
        check(sub.currentPeriodEnd!!.isAfter(now)) { "기간이 남아 있어야 하는 시나리오" }

        stubEmptyDefaults()
        // 기간이 남은 취소 구독은 findCancelledExpired(current_period_end < now)의
        // 조회 결과에 애초에 포함되지 않는다.
        every { subscriptionRepository.findCancelledExpired(any()) } returns emptyList()

        // when
        billingScheduler.processBilling()

        // then - Free 전환이 일어나지 않아야 함
        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }

    @Test
    @DisplayName("다운그레이드 예약이 기간 만료 후 적용되어야 한다")
    fun `다운그레이드 예약 적용`() {
        // given
        val sub = createSubscription(
            id = 7L,
            userId = 7L,
            planType = PlanType.BUSINESS,
            status = SubscriptionStatus.ACTIVE,
            billingCycle = BillingCycle.MONTHLY,
            currentPeriodEnd = now.minusHours(1), // 기간 만료됨
            pendingPlanType = PlanType.STARTER,
        )
        val user = createUser(7L, PlanType.BUSINESS)
        val credit = AiCredit(
            id = 1L,
            userId = 7L,
            balance = 500,
            freeMonthly = PlanType.BUSINESS.freeCredits,
            freeRemaining = 200,
            freeResetDate = LocalDate.now().withDayOfMonth(1).plusMonths(1),
        )

        stubEmptyDefaults()
        every { subscriptionRepository.findWithPendingPlanType() } returns listOf(sub)
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { userRepository.findById(7L) } returns user
        every { userRepository.update(any()) } answers { firstArg() }
        every { creditRepository.findByUserId(7L) } returns credit
        every { creditRepository.update(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }

        // when
        billingScheduler.processBilling()

        // then
        val subSlot = slot<Subscription>()
        verify { subscriptionRepository.update(capture(subSlot)) }
        assertEquals(PlanType.STARTER, subSlot.captured.planType)
        assertEquals(PlanType.STARTER.price, subSlot.captured.price)
        assertNull(subSlot.captured.pendingPlanType)
        assertEquals(SubscriptionStatus.ACTIVE, subSlot.captured.status)

        val userSlot = slot<User>()
        verify { userRepository.update(capture(userSlot)) }
        assertEquals(PlanType.STARTER, userSlot.captured.planType)

        /*
         * 예전에는 freeMonthly 만 직접 갱신해서, 하향 후에도 freeRemaining·balance 가
         * BUSINESS 기준으로 남아 상위 플랜만큼 쓸 수 있었다. 이제 entitlement API 가
         * 세 값을 한 번에 맞춘다.
         */
        verify(exactly = 1) {
            creditService.applyPlanEntitlement(7L, PlanType.STARTER, "PENDING_PLAN_CHANGE")
        }
        verify(exactly = 0) { creditRepository.update(any()) }

        val notifSlot = slot<Notification>()
        verify { notificationRepository.save(capture(notifSlot)) }
        assertEquals(7L, notifSlot.captured.userId)
        assertEquals("플랜 변경 완료", notifSlot.captured.title)
    }

    @Test
    @DisplayName("다운그레이드 예약이 FREE로 변경되면 status도 FREE가 되어야 한다")
    fun `다운그레이드 FREE 전환 시 status FREE`() {
        // given
        val sub = createSubscription(
            id = 8L,
            userId = 8L,
            planType = PlanType.STARTER,
            status = SubscriptionStatus.ACTIVE,
            billingCycle = BillingCycle.MONTHLY,
            currentPeriodEnd = now.minusHours(1),
            pendingPlanType = PlanType.FREE,
        )
        val user = createUser(8L, PlanType.STARTER)
        val credit = AiCredit(
            id = 2L,
            userId = 8L,
            balance = 50,
            freeMonthly = PlanType.STARTER.freeCredits,
            freeRemaining = 50,
            freeResetDate = LocalDate.now().withDayOfMonth(1).plusMonths(1),
        )

        stubEmptyDefaults()
        every { subscriptionRepository.findWithPendingPlanType() } returns listOf(sub)
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { userRepository.findById(8L) } returns user
        every { userRepository.update(any()) } answers { firstArg() }
        every { creditRepository.findByUserId(8L) } returns credit
        every { creditRepository.update(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }

        // when
        billingScheduler.processBilling()

        // then
        val subSlot = slot<Subscription>()
        verify { subscriptionRepository.update(capture(subSlot)) }
        assertEquals(PlanType.FREE, subSlot.captured.planType)
        assertEquals(SubscriptionStatus.FREE, subSlot.captured.status)
        assertEquals(0, subSlot.captured.price) // FREE.price = 0
    }

    @Test
    @DisplayName("연간 빌링 다운그레이드 시 yearlyPrice가 적용되어야 한다")
    fun `연간 빌링 다운그레이드 - yearlyPrice 적용`() {
        // given
        val sub = createSubscription(
            id = 9L,
            userId = 9L,
            planType = PlanType.BUSINESS,
            status = SubscriptionStatus.ACTIVE,
            billingCycle = BillingCycle.YEARLY,
            currentPeriodEnd = now.minusHours(1),
            pendingPlanType = PlanType.PRO,
        )
        val user = createUser(9L, PlanType.BUSINESS)
        val credit = AiCredit(
            id = 3L,
            userId = 9L,
            balance = 300,
            freeMonthly = PlanType.BUSINESS.freeCredits,
            freeRemaining = 200,
            freeResetDate = LocalDate.now().withDayOfMonth(1).plusMonths(1),
        )

        stubEmptyDefaults()
        every { subscriptionRepository.findWithPendingPlanType() } returns listOf(sub)
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { userRepository.findById(9L) } returns user
        every { userRepository.update(any()) } answers { firstArg() }
        every { creditRepository.findByUserId(9L) } returns credit
        every { creditRepository.update(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }

        // when
        billingScheduler.processBilling()

        // then
        val subSlot = slot<Subscription>()
        verify { subscriptionRepository.update(capture(subSlot)) }
        assertEquals(PlanType.PRO, subSlot.captured.planType)
        assertEquals(PlanType.PRO.yearlyPrice, subSlot.captured.price)
        assertEquals(SubscriptionStatus.ACTIVE, subSlot.captured.status)
    }

    @Test
    @DisplayName("예약 주기만 남은 구독은 결제하지 않고 수동 확인 로그 대상으로 남겨야 한다")
    fun `예약 주기만 남은 이상 행은 건너뜀`() {
        val sub = createSubscription(
            id = 10L,
            userId = 10L,
            planType = PlanType.PRO,
            status = SubscriptionStatus.ACTIVE,
            billingCycle = BillingCycle.YEARLY,
            currentPeriodEnd = now.minusHours(1),
            pendingPlanType = null,
            pendingBillingCycle = BillingCycle.MONTHLY,
        )

        stubEmptyDefaults()
        every { subscriptionRepository.findWithPendingPlanType() } returns listOf(sub)

        billingScheduler.processBilling()

        verify(exactly = 0) { subscriptionRepository.update(any()) }
        verify(exactly = 0) { renewalService.renew(any(), any()) }
        verify(exactly = 0) { userRepository.update(any()) }
        verify(exactly = 0) { notificationRepository.save(any()) }
    }

    @Test
    @DisplayName("처리할 데이터가 없으면 아무 변경도 일어나지 않아야 한다")
    fun `처리할 데이터 없음 - 변경 없음`() {
        // given
        stubEmptyDefaults()

        // when
        billingScheduler.processBilling()

        // then
        verify(exactly = 0) { subscriptionRepository.update(any()) }
        verify(exactly = 0) { userRepository.update(any()) }
        verify(exactly = 0) { notificationRepository.save(any()) }
        verify(exactly = 0) { creditRepository.update(any()) }
    }

    /**
     * **기간이 비어 있는 유료 취소 구독도 Free 로 내려간다.**
     *
     * `current_period_end` 는 NOT NULL 이 아니고, SQL 에서 NULL 과의 `<` 비교는 UNKNOWN
     * 이라 조회 조건이 `current_period_end < now` 뿐이면 그런 행이 영원히 선택되지 않았다.
     * 다른 조회도 그 행을 잡지 못해(체험 컬럼 없음·다른 status 요구·취소가 pending 을
     * 비움) **유료 planType 이 영구히 남았다.**
     *
     * 조회 조건은 [com.ongo.infrastructure.persistence.jooq.SubscriptionJooqRepository]
     * 쪽 SQL 계약 테스트가 본다. 여기서는 그렇게 전달된 행이 **강등 경로를 끝까지 타는지**
     * 를 고정한다 — 구독·사용자·크레딧 권한이 모두 Free 가 되어야 한다.
     */
    @Test
    @DisplayName("기간이 NULL 인 유료 취소 구독도 Free 로 전환한다")
    fun `취소 만료 - 기간 NULL 도 강등`() {
        stubEmptyDefaults()
        val nullPeriod = createSubscription(
            id = 9L,
            userId = 99L,
            planType = PlanType.PRO,
            status = SubscriptionStatus.CANCELLED,
            currentPeriodEnd = null,
        )
        every { subscriptionRepository.findCancelledExpired(any()) } returns listOf(nullPeriod)
        every { userRepository.findById(99L) } returns createUser(99L)
        every { userRepository.update(any()) } answers { firstArg() }
        every { subscriptionRepository.update(any()) } answers { firstArg() }

        billingScheduler.processBilling()

        verify(exactly = 1) {
            subscriptionRepository.update(
                match { it.id == 9L && it.planType == PlanType.FREE && it.status == SubscriptionStatus.FREE },
            )
        }
        verify(exactly = 1) { userRepository.update(match { it.id == 99L && it.planType == PlanType.FREE }) }
        verify(exactly = 1) {
            creditService.applyPlanEntitlement(99L, PlanType.FREE, "SUBSCRIPTION_CANCELLED")
        }
    }

    /** 기간이 남아 있는 취소 구독까지 앞당겨 끊지 않는다 — 과도한 전환 회귀를 막는다. */
    @Test
    @DisplayName("조회에 걸리지 않은 취소 구독은 건드리지 않는다")
    fun `취소 만료 - 대상 아님`() {
        stubEmptyDefaults()

        billingScheduler.processBilling()

        verify(exactly = 0) { subscriptionRepository.update(any()) }
        verify(exactly = 0) { creditService.applyPlanEntitlement(any(), any(), any()) }
    }
}
