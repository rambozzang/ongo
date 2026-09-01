package com.ongo.application.subscription

import com.ongo.application.storage.StorageQuotaUseCase
import com.ongo.common.enums.AuthProvider
import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.domain.credit.CreditRepository
import com.ongo.domain.lock.DistributedLockPort
import com.ongo.domain.notification.NotificationRepository
import com.ongo.domain.storage.StorageQuotaPort
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.User
import com.ongo.domain.user.UserRepository
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime

/**
 * `subscriptions.storage_quota_limit_bytes` 가 **유료 시절 한도를 남기지 않는지** 고정한다.
 *
 * ## 무엇이 깨져 있었나
 *
 * 이 컬럼은 [StorageQuotaUseCase.getEffectiveLimit] 에서 플랜을 **무시하는 절대 오버라이드**로
 * 읽힌다(`컬럼 ?: plan.storageBytes`). 그런데 유료 결제가 확정될 때마다 그 플랜의 기본
 * 저장공간이 이 컬럼에 적혔고, 어떤 하향 경로도 그것을 지우지 않았다.
 *
 * 결과는 **결제 없이 유료 저장공간을 무기한 쓰는 상태**다. BUSINESS(200GB)를 한 달 쓰고
 * 해지한 계정이 FREE 로 내려간 뒤에도 200GB 를 그대로 쓴다. 환불받은 계정도 같다.
 *
 * 같은 뿌리에서 반대 방향 사고도 있었다. 관리자가 CS 로 올려 준 한도
 * (`AdminUseCase.updateStorageQuota`)가 **다음 결제 한 번에 조용히 사라졌다.**
 *
 * ## 이제 컬럼의 뜻
 *
 * **관리자 오버라이드 하나뿐이다.** 플랜 기본값은 아무도 여기 적지 않고, fallback 이
 * 대신한다. 그래서 하향·환불 경로는 이 값을 비우기만 하면 된다.
 */
@ExtendWith(MockKExtension::class)
class StorageQuotaOverrideLifecycleTest {

    @MockK private lateinit var subscriptionRepository: SubscriptionRepository
    @MockK private lateinit var userRepository: UserRepository
    @MockK private lateinit var notificationRepository: NotificationRepository
    @MockK private lateinit var creditRepository: CreditRepository
    @MockK private lateinit var distributedLockPort: DistributedLockPort
    @MockK private lateinit var renewalService: SubscriptionRenewalService
    @MockK(relaxUnitFun = true) private lateinit var creditService: com.ongo.application.credit.CreditService

    private lateinit var billingScheduler: BillingScheduler

    private val now: LocalDateTime = LocalDateTime.now()

    /** 유료 시절에 적혀 남아 있는 값. 이 테스트들이 지우려는 대상이다. */
    private val businessBytes = PlanType.BUSINESS.storageBytes
    private val proBytes = PlanType.PRO.storageBytes

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { distributedLockPort.withLock(any(), any<() -> Unit>()) } answers {
            secondArg<() -> Unit>().invoke()
            true
        }
        every { subscriptionRepository.findTrialExpired(any()) } returns emptyList()
        every { subscriptionRepository.findPausedToResume(any()) } returns emptyList()
        every { subscriptionRepository.findPastDue(3) } returns emptyList()
        every { subscriptionRepository.findPastDue(7) } returns emptyList()
        every { subscriptionRepository.findDueForBilling(any()) } returns emptyList()
        every { subscriptionRepository.findCancelledExpired(any()) } returns emptyList()
        every { subscriptionRepository.findWithPendingPlanType() } returns emptyList()
        every { userRepository.findById(any()) } answers {
            User(
                id = firstArg(),
                email = "user@test.com",
                name = "tester",
                provider = AuthProvider.GOOGLE,
                providerId = "google-1",
                planType = PlanType.BUSINESS,
            )
        }
        every { userRepository.update(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }
        every { subscriptionRepository.update(any()) } answers { firstArg() }

        billingScheduler = BillingScheduler(
            subscriptionRepository,
            userRepository,
            notificationRepository,
            creditRepository,
            distributedLockPort,
            creditService,
            renewalService,
            DummyTransactionManagerForTest(),
            // 이 파일은 갱신이 아니라 하향 경로만 본다.
            renewalEnabled = false,
        )
    }

    private fun paidSubscription(
        plan: PlanType = PlanType.BUSINESS,
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        overrideBytes: Long? = null,
        pendingPlanType: PlanType? = null,
        trialEnd: LocalDateTime? = null,
    ) = Subscription(
        id = 1L,
        userId = 1L,
        planType = plan,
        status = status,
        price = plan.price,
        billingCycle = BillingCycle.MONTHLY,
        currentPeriodStart = now.minusDays(30),
        currentPeriodEnd = now.minusDays(1),
        nextBillingDate = now.minusDays(1),
        pendingPlanType = pendingPlanType,
        storageQuotaLimitBytes = overrideBytes,
        trialEnd = trialEnd,
    )

    /** 스케줄러가 저장한 구독 한 건을 집어낸다. */
    private fun capturedUpdate(): Subscription {
        val captured = slot<Subscription>()
        io.mockk.verify { subscriptionRepository.update(capture(captured)) }
        return captured.captured
    }

    /* ── FREE 전환 경로 ──────────────────────────────────────────────── */

    /**
     * **핵심 회귀.** 해지 후 기간이 끝나 FREE 로 내려가면 유료 저장공간도 함께 끝나야 한다.
     * 남으면 결제하지 않는 계정이 유료 한도를 계속 쓴다.
     */
    @Test
    @DisplayName("취소 만료 FREE 전환이 저장공간 오버라이드를 지운다")
    fun cancelledExpiryClearsOverride() {
        every { subscriptionRepository.findCancelledExpired(any()) } returns
            listOf(paidSubscription(plan = PlanType.BUSINESS, overrideBytes = businessBytes))

        billingScheduler.processBilling()

        val saved = capturedUpdate()
        assertEquals(PlanType.FREE, saved.planType)
        assertNull(saved.storageQuotaLimitBytes, "FREE 로 내려갔는데 BUSINESS 한도가 남았다")
    }

    /** 미납 7일 강등도 같다 — 돈이 들어오지 않은 계정이다. */
    @Test
    @DisplayName("미납 7일 FREE 전환이 저장공간 오버라이드를 지운다")
    fun pastDueDowngradeClearsOverride() {
        every { subscriptionRepository.findPastDue(7) } returns
            listOf(paidSubscription(plan = PlanType.PRO, status = SubscriptionStatus.PAST_DUE, overrideBytes = proBytes))

        billingScheduler.processBilling()

        val saved = capturedUpdate()
        assertEquals(PlanType.FREE, saved.planType)
        assertNull(saved.storageQuotaLimitBytes)
    }

    /** 체험은 결제가 없다. 만료 뒤에도 유료 한도가 남으면 체험이 영구 혜택이 된다. */
    @Test
    @DisplayName("체험 만료가 저장공간 오버라이드를 지운다")
    fun trialExpiryClearsOverride() {
        every { subscriptionRepository.findTrialExpired(any()) } returns
            listOf(
                paidSubscription(
                    plan = PlanType.STARTER,
                    status = SubscriptionStatus.TRIALING,
                    overrideBytes = PlanType.STARTER.storageBytes,
                    trialEnd = now.minusDays(1),
                ),
            )

        billingScheduler.processBilling()

        val saved = capturedUpdate()
        assertEquals(PlanType.FREE, saved.planType)
        assertNull(saved.storageQuotaLimitBytes)
    }

    /* ── 유료 → 유료 하향 ────────────────────────────────────────────── */

    /**
     * 예약 하향도 지운다. `pendingPlanType` 은 **하향 전용**이다 — 상향은
     * `SubscriptionUseCase.changePlan` 이 `PAYMENT_REQUIRED` 로 막고 결제 경로로만 간다.
     *
     * 지우지 않으면 STARTER 로 내려간 계정이 PRO 한도(50GB)를 그대로 쓴다.
     */
    @Test
    @DisplayName("PRO→STARTER 예약 하향이 저장공간 오버라이드를 지운다")
    fun pendingDowngradeClearsOverride() {
        every { subscriptionRepository.findWithPendingPlanType() } returns
            listOf(
                paidSubscription(
                    plan = PlanType.PRO,
                    overrideBytes = proBytes,
                    pendingPlanType = PlanType.STARTER,
                ),
            )

        billingScheduler.processBilling()

        val saved = capturedUpdate()
        assertEquals(PlanType.STARTER, saved.planType)
        assertNull(saved.storageQuotaLimitBytes, "PRO 한도가 STARTER 계정에 남았다")
    }

    /** BUSINESS 예약 하향도 같은 규칙이다 — 가장 큰 한도가 가장 오래 남는다. */
    @Test
    @DisplayName("BUSINESS→FREE 예약 하향이 저장공간 오버라이드를 지운다")
    fun businessPendingDowngradeClearsOverride() {
        every { subscriptionRepository.findWithPendingPlanType() } returns
            listOf(
                paidSubscription(
                    plan = PlanType.BUSINESS,
                    overrideBytes = businessBytes,
                    pendingPlanType = PlanType.FREE,
                ),
            )

        billingScheduler.processBilling()

        val saved = capturedUpdate()
        assertEquals(PlanType.FREE, saved.planType)
        assertNull(saved.storageQuotaLimitBytes)
    }

    /* ── 정리 후 실제로 보이는 한도 ──────────────────────────────────── */

    /**
     * 컬럼을 비우는 것만으로 **FREE 기본 한도로 돌아가는지** 끝까지 확인한다.
     *
     * 위 테스트들은 저장된 값만 본다. 사용자가 실제로 보는 숫자는 fallback 이 정하므로,
     * 그 fallback 이 살아 있다는 것까지 함께 고정해야 의미가 있다.
     */
    @Test
    @DisplayName("오버라이드를 비우면 실효 한도가 플랜 기본값으로 돌아간다")
    fun clearedOverrideFallsBackToPlanDefault() {
        val repository = mockk<SubscriptionRepository>()
        val quota = StorageQuotaUseCase(repository, mockk<StorageQuotaPort>())

        every { repository.findByUserId(1L) } returns
            paidSubscription(plan = PlanType.FREE, status = SubscriptionStatus.FREE, overrideBytes = null)

        assertEquals(PlanType.FREE.storageBytes, quota.getEffectiveLimit(1L))
    }

    /** 지우지 않았을 때 어떤 값이 나오는지도 남긴다 — 이 테스트들이 막는 것이 정확히 이 숫자다. */
    @Test
    @DisplayName("오버라이드가 남아 있으면 FREE 계정도 유료 한도를 그대로 쓴다")
    fun staleOverrideWouldKeepPaidLimit() {
        val repository = mockk<SubscriptionRepository>()
        val quota = StorageQuotaUseCase(repository, mockk<StorageQuotaPort>())

        every { repository.findByUserId(1L) } returns
            paidSubscription(plan = PlanType.FREE, status = SubscriptionStatus.FREE, overrideBytes = businessBytes)

        assertEquals(businessBytes, quota.getEffectiveLimit(1L))
    }

    /** 관리자 오버라이드는 fallback 보다 우선한다 — 이 계약은 종전 그대로다. */
    @Test
    @DisplayName("관리자 오버라이드는 플랜 기본값을 이긴다")
    fun adminOverrideBeatsPlanDefault() {
        val repository = mockk<SubscriptionRepository>()
        val quota = StorageQuotaUseCase(repository, mockk<StorageQuotaPort>())
        val granted = PlanType.BUSINESS.storageBytes * 2

        every { repository.findByUserId(1L) } returns
            paidSubscription(plan = PlanType.STARTER, overrideBytes = granted)

        assertEquals(granted, quota.getEffectiveLimit(1L))
    }
}
