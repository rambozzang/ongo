package com.ongo.application.subscription

import com.ongo.application.paddle.PaddleGateway
import com.ongo.application.storage.StorageQuotaUseCase
import com.ongo.application.subscription.dto.ChangePlanRequest
import com.ongo.common.enums.AuthProvider
import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.User
import com.ongo.domain.user.UserRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SubscriptionUseCaseTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val userRepository = mockk<UserRepository>()
    private val videoRepository = mockk<VideoRepository>()
    private val paddleGateway = mockk<PaddleGateway>()
    private val storageQuotaUseCase = mockk<StorageQuotaUseCase>()
    private val creditService = mockk<com.ongo.application.credit.CreditService>(relaxUnitFun = true)
    private val activityLogUseCase =
        mockk<com.ongo.application.activitylog.ActivityLogUseCase>(relaxed = true)

    private lateinit var useCase: SubscriptionUseCase

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        useCase = SubscriptionUseCase(
            subscriptionRepository,
            userRepository,
            videoRepository,
            paddleGateway,
            storageQuotaUseCase,
            creditService,
            activityLogUseCase,
        )
    }

    private fun createSubscription(
        id: Long = 1L,
        userId: Long = 100L,
        planType: PlanType = PlanType.STARTER,
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        price: Int = planType.price,
        billingCycle: BillingCycle = BillingCycle.MONTHLY,
        currentPeriodEnd: LocalDateTime? = LocalDateTime.now().plusDays(15),
    ) = Subscription(
        id = id,
        userId = userId,
        planType = planType,
        status = status,
        price = price,
        billingCycle = billingCycle,
        currentPeriodStart = LocalDateTime.now(),
        currentPeriodEnd = currentPeriodEnd,
    )

    private fun createUser(
        id: Long = 100L,
        planType: PlanType = PlanType.STARTER,
    ) = User(
        id = id,
        email = "test@example.com",
        name = "Test User",
        provider = AuthProvider.GOOGLE,
        providerId = "google_123",
        planType = planType,
    )

    @Test
    fun `getCurrentSubscription should return subscription response`() {
        val subscription = createSubscription()

        every { subscriptionRepository.findByUserId(100L) } returns subscription

        val response = useCase.getCurrentSubscription(100L)

        assertEquals(PlanType.STARTER, response.planType)
        assertEquals(SubscriptionStatus.ACTIVE, response.status)
        assertEquals(PlanType.STARTER.price, response.price)
        assertEquals(BillingCycle.MONTHLY, response.billingCycle)
    }

    @Test
    fun `getCurrentSubscription should throw NotFoundException when no subscription`() {
        every { subscriptionRepository.findByUserId(999L) } returns null

        assertFailsWith<NotFoundException> {
            useCase.getCurrentSubscription(999L)
        }
    }

    /**
     * 예전에는 이 경로가 **결제 없이** planType·price 를 즉시 갱신했다. proratedAmount 를
     * 계산해 응답에 담기만 했을 뿐 Payment 생성도 PG 호출도 없어서, STARTER 고객이 이 API
     * 한 번으로 PRO 를 얻었다.
     *
     * 상향은 결제가 확정된 뒤 completeSubscription 한 곳에서만 적용돼야 한다.
     */
    @Test
    fun `changePlan upgrade without payment is rejected and nothing is updated`() {
        val subscription = createSubscription(
            planType = PlanType.STARTER,
            currentPeriodEnd = LocalDateTime.now().plusDays(15),
        )
        val user = createUser(planType = PlanType.STARTER)
        val request = ChangePlanRequest(targetPlan = "PRO", billingCycle = "MONTHLY")

        every { subscriptionRepository.findByUserId(100L) } returns subscription
        every { userRepository.findById(100L) } returns user

        val e = assertFailsWith<BusinessException> { useCase.changePlan(100L, request) }

        assertEquals("PAYMENT_REQUIRED", e.code)
        verify(exactly = 0) { subscriptionRepository.update(any()) }
        verify(exactly = 0) { userRepository.update(any()) }
    }

    /**
     * 예전에는 아무것도 하지 않고 **성공 응답**을 돌려줬다. 호출자는 요청이 받아들여진 줄
     * 알고, 프런트가 체크아웃을 여는 분기를 놓치면 사용자는 플랜이 바뀐 줄 안다.
     */
    @Test
    fun `changePlan from FREE to paid is rejected with PAYMENT_REQUIRED`() {
        val subscription = createSubscription(planType = PlanType.FREE, price = 0)
        val user = createUser(planType = PlanType.FREE)
        val request = ChangePlanRequest(targetPlan = "STARTER", billingCycle = "MONTHLY")

        every { subscriptionRepository.findByUserId(100L) } returns subscription
        every { userRepository.findById(100L) } returns user

        val e = assertFailsWith<BusinessException> { useCase.changePlan(100L, request) }

        assertEquals("PAYMENT_REQUIRED", e.code)
        verify(exactly = 0) { subscriptionRepository.update(any()) }
        verify(exactly = 0) { userRepository.update(any()) }
    }

    @Test
    fun `changePlan billing cycle without payment is rejected`() {
        val subscription = createSubscription(
            planType = PlanType.STARTER,
            billingCycle = BillingCycle.MONTHLY,
        )
        val user = createUser(planType = PlanType.STARTER)
        val request = ChangePlanRequest(targetPlan = "STARTER", billingCycle = "YEARLY")

        every { subscriptionRepository.findByUserId(100L) } returns subscription
        every { userRepository.findById(100L) } returns user

        val e = assertFailsWith<BusinessException> { useCase.changePlan(100L, request) }

        assertEquals("PAYMENT_REQUIRED", e.code)
        verify(exactly = 0) { subscriptionRepository.update(any()) }
        verify(exactly = 0) { userRepository.update(any()) }
    }

    @Test
    fun `changePlan downgrade with a different billing cycle is scheduled together`() {
        val subscription = createSubscription(
            planType = PlanType.PRO,
            billingCycle = BillingCycle.MONTHLY,
        )
        val user = createUser(planType = PlanType.PRO)
        val request = ChangePlanRequest(targetPlan = "STARTER", billingCycle = "YEARLY")

        every { subscriptionRepository.findByUserId(100L) } returns subscription
        every { userRepository.findById(100L) } returns user
        every { subscriptionRepository.update(any()) } answers { firstArg() }

        useCase.changePlan(100L, request)

        verify {
            subscriptionRepository.update(match {
                it.pendingPlanType == PlanType.STARTER &&
                    it.pendingBillingCycle == BillingCycle.YEARLY &&
                    it.billingCycle == BillingCycle.MONTHLY
            })
        }
    }

    @Test
    fun `changePlan from annual to monthly for the same plan is scheduled without payment`() {
        val subscription = createSubscription(
            planType = PlanType.STARTER,
            billingCycle = BillingCycle.YEARLY,
            price = PlanType.STARTER.yearlyPrice,
        )
        val user = createUser(planType = PlanType.STARTER)
        val request = ChangePlanRequest(targetPlan = "STARTER", billingCycle = "MONTHLY")

        every { subscriptionRepository.findByUserId(100L) } returns subscription
        every { userRepository.findById(100L) } returns user
        every { subscriptionRepository.update(any()) } answers { firstArg() }

        useCase.changePlan(100L, request)

        verify {
            subscriptionRepository.update(match {
                it.pendingPlanType == PlanType.STARTER &&
                    it.pendingBillingCycle == BillingCycle.MONTHLY &&
                    it.billingCycle == BillingCycle.YEARLY
            })
        }
    }

    /**
     * 체험은 planType 이 유료인데 결제가 없다. 상향이든 하향이든 이 API 로는 바꿀 수 없다 —
     * 상향은 결제를 거쳐야 하고, 해지는 취소 API 의 몫이다.
     */
    @Test
    fun `changePlan during trial is rejected with PAYMENT_REQUIRED`() {
        val subscription = createSubscription(
            planType = PlanType.STARTER,
            status = SubscriptionStatus.TRIALING,
            currentPeriodEnd = LocalDateTime.now().plusDays(5),
        )
        val user = createUser(planType = PlanType.STARTER)

        every { subscriptionRepository.findByUserId(100L) } returns subscription
        every { userRepository.findById(100L) } returns user

        for (target in listOf("PRO", "FREE")) {
            val e = assertFailsWith<BusinessException> {
                useCase.changePlan(100L, ChangePlanRequest(targetPlan = target, billingCycle = "MONTHLY"))
            }
            assertEquals("PAYMENT_REQUIRED", e.code)
        }
        verify(exactly = 0) { subscriptionRepository.update(any()) }
        verify(exactly = 0) { userRepository.update(any()) }
    }

    /**
     * Paddle 구독은 결제 수단이 이미 연결돼 있고 Paddle 이 일할 청구를 처리한다.
     * 레거시지만 기존 계약을 깨지 않는다.
     */
    @Test
    fun `changePlan upgrade through Paddle still delegates to the gateway`() {
        val subscription = createSubscription(
            planType = PlanType.STARTER,
            currentPeriodEnd = LocalDateTime.now().plusDays(15),
        ).copy(paddleSubscriptionId = "sub_paddle_1")
        val user = createUser(planType = PlanType.STARTER)
        val request = ChangePlanRequest(targetPlan = "PRO", billingCycle = "MONTHLY")

        every { subscriptionRepository.findByUserId(100L) } returns subscription
        every { userRepository.findById(100L) } returns user
        every { paddleGateway.getPriceIdForPlan("PRO", "MONTHLY") } returns "pri_pro"
        every { paddleGateway.updateSubscription(any(), any(), any()) } returns Unit

        val response = useCase.changePlan(100L, request)

        assertNull(response.proratedAmount)
        verify(exactly = 1) {
            paddleGateway.updateSubscription("sub_paddle_1", "pri_pro", "prorated_immediately")
        }
        // Paddle 이 웹훅으로 동기화한다. 여기서 직접 갱신하면 두 진실이 갈라진다.
        verify(exactly = 0) { subscriptionRepository.update(any()) }
        verify(exactly = 0) { userRepository.update(any()) }
    }

    @Test
    fun `changePlan downgrade should set pendingPlanType for next period`() {
        val subscription = createSubscription(
            planType = PlanType.PRO,
            price = PlanType.PRO.price,
            currentPeriodEnd = LocalDateTime.now().plusDays(20),
        )
        val user = createUser(planType = PlanType.PRO)
        val request = ChangePlanRequest(targetPlan = "STARTER", billingCycle = "MONTHLY")

        every { subscriptionRepository.findByUserId(100L) } returns subscription
        every { userRepository.findById(100L) } returns user
        every { subscriptionRepository.update(any()) } answers { firstArg() }

        val response = useCase.changePlan(100L, request)

        assertNull(response.proratedAmount)
        verify { subscriptionRepository.update(match { it.pendingPlanType == PlanType.STARTER }) }
        verify(exactly = 0) { userRepository.update(any()) }
    }

    @Test
    fun `changePlan with the current plan and cycle is a no-op`() {
        val subscription = createSubscription(planType = PlanType.STARTER, billingCycle = BillingCycle.MONTHLY)
        val user = createUser(planType = PlanType.STARTER)
        val request = ChangePlanRequest(targetPlan = "STARTER", billingCycle = "MONTHLY")

        every { subscriptionRepository.findByUserId(100L) } returns subscription
        every { userRepository.findById(100L) } returns user

        val response = useCase.changePlan(100L, request)

        assertEquals(PlanType.STARTER, response.subscription.planType)
        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }

    @Test
    fun `cancelSubscription should set CANCELLED status and cancelledAt`() {
        val subscription = createSubscription()

        every { subscriptionRepository.findByUserId(100L) } returns subscription
        every { subscriptionRepository.update(any()) } answers { firstArg() }

        val response = useCase.cancelSubscription(100L)

        assertEquals(SubscriptionStatus.CANCELLED, response.status)
        verify {
            subscriptionRepository.update(match {
                it.status == SubscriptionStatus.CANCELLED &&
                    it.cancelledAt != null &&
                    it.pendingPlanType == null
            })
        }
    }

    @Test
    fun `getPlans should return all plans with current plan`() {
        val user = createUser(planType = PlanType.PRO)

        every { userRepository.findById(100L) } returns user

        val response = useCase.getPlans(100L)

        assertEquals(PlanType.entries.size, response.plans.size)
        assertEquals(PlanType.PRO, response.currentPlan)
        assert(response.plans.any { it.recommended && it.planType == PlanType.PRO })
    }

    @Test
    fun `getUsage should calculate uploads and storage correctly`() {
        every { videoRepository.countByUserIdAndMonth(100L, any<YearMonth>()) } returns 5L
        every { storageQuotaUseCase.getCurrentUsage(100L) } returns 30L * 1024 * 1024
        every { storageQuotaUseCase.getEffectiveLimit(100L) } returns 50L * 1024 * 1024 * 1024

        val response = useCase.getUsage(100L)

        assertEquals(5, response.uploadsThisMonth)
        assertEquals(30L, response.storageUsedMb)
        assertEquals(50L * 1024 * 1024 * 1024, response.storageLimitBytes)
        verify(exactly = 1) { storageQuotaUseCase.getCurrentUsage(100L) }
        verify(exactly = 1) { storageQuotaUseCase.getEffectiveLimit(100L) }
    }

    @Test
    fun `initializeSubscription should create FREE plan subscription`() {
        every { subscriptionRepository.save(any()) } answers { firstArg() }

        val subscription = useCase.initializeSubscription(100L)

        assertEquals(PlanType.FREE, subscription.planType)
        assertEquals(SubscriptionStatus.FREE, subscription.status)
        assertEquals(0, subscription.price)
        assertEquals(BillingCycle.MONTHLY, subscription.billingCycle)
        assertEquals(100L, subscription.userId)

        verify {
            subscriptionRepository.save(match {
                it.planType == PlanType.FREE &&
                    it.status == SubscriptionStatus.FREE &&
                    it.userId == 100L
            })
        }
    }

    // ──────────────────────────────────────────────
    // startTrial — 체험 시작과 크레딧 권한
    // ──────────────────────────────────────────────

    private fun freeSubscription(trialStart: java.time.LocalDateTime? = null) = createSubscription(
        planType = PlanType.FREE,
        status = SubscriptionStatus.FREE,
    ).copy(trialStart = trialStart)

    /**
     * 체험을 시작해도 크레딧이 FREE 기준(30)에 머물면 쇼츠 실행 한 번(37)도 못 돌린다.
     * 체험의 목적이 완성된 결과를 보여주는 것인데 그게 불가능해진다.
     */
    @Test
    fun `체험을 시작하면 같은 트랜잭션에서 Starter 크레딧 권한을 적용한다`() {
        every { subscriptionRepository.findByUserId(100L) } returns freeSubscription()
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { userRepository.findById(100L) } returns mockk(relaxed = true)
        every { userRepository.update(any()) } answers { firstArg() }

        useCase.startTrial(100L, "STARTER")

        verify(exactly = 1) {
            creditService.applyPlanEntitlement(100L, PlanType.STARTER, "TRIAL_START")
        }
        /*
         * 퍼널의 분자다. 구독·사용자·크레딧이 모두 반영된 뒤에만 남으므로, 중간에 실패한
         * 시도는 체험을 시작한 것으로 세지 않는다. 내용·IP·User-Agent 는 담지 않는다.
         */
        verify(exactly = 1) {
            activityLogUseCase.logActivity(
                userId = 100L,
                action = com.ongo.application.activitylog.ActivityLogActions.SUBSCRIPTION_TRIAL_STARTED,
                entityType = com.ongo.application.activitylog.ActivityLogActions.ENTITY_SUBSCRIPTION,
                entityId = null,
            )
        }
    }

    @Test
    fun `Starter 이외의 체험 거부는 체험 시작 사건을 남기지 않는다`() {
        every { subscriptionRepository.findByUserId(100L) } returns freeSubscription()

        assertFailsWith<IllegalStateException> { useCase.startTrial(100L, "BUSINESS") }

        verify(exactly = 0) { activityLogUseCase.logActivity(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `이미 체험한 사용자의 재시도는 체험 시작 사건을 남기지 않는다`() {
        every { subscriptionRepository.findByUserId(100L) } returns
            freeSubscription(trialStart = java.time.LocalDateTime.now().minusDays(30))

        assertFailsWith<IllegalStateException> { useCase.startTrial(100L, "STARTER") }

        verify(exactly = 0) { activityLogUseCase.logActivity(any(), any(), any(), any(), any(), any(), any()) }
    }

    /*
     * 제품 CTA 는 STARTER 만 보낸다. 다른 플랜을 허용하면 API 를 직접 불러 BUSINESS
     * 체험을 여는 경로가 생기고, 체험은 1회성이라 되돌릴 방법이 없다.
     */
    @Test
    fun `Starter 이외의 체험은 거부하고 크레딧을 건드리지 않는다`() {
        every { subscriptionRepository.findByUserId(100L) } returns freeSubscription()

        listOf("PRO", "BUSINESS", "FREE").forEach { plan ->
            assertFailsWith<IllegalStateException>("$plan 체험이 통과했다") {
                useCase.startTrial(100L, plan)
            }
        }

        verify(exactly = 0) { creditService.applyPlanEntitlement(any(), any(), any()) }
        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }

    @Test
    fun `이미 체험한 사용자는 거부하고 크레딧을 건드리지 않는다`() {
        every { subscriptionRepository.findByUserId(100L) } returns
            freeSubscription(trialStart = java.time.LocalDateTime.now().minusDays(30))

        assertFailsWith<IllegalStateException> { useCase.startTrial(100L, "STARTER") }

        verify(exactly = 0) { creditService.applyPlanEntitlement(any(), any(), any()) }
        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }

    /*
     * 구독 갱신이 실패했는데 크레딧만 남으면, 체험을 시작하지 않은 사용자가 Starter
     * 크레딧을 들고 있게 된다. 같은 @Transactional 이라 함께 롤백되지만, 여기서는
     * 예외가 호출자까지 전파되는지를 고정한다.
     */
    @Test
    fun `구독 갱신이 실패하면 예외가 전파된다`() {
        every { subscriptionRepository.findByUserId(100L) } returns freeSubscription()
        every { subscriptionRepository.update(any()) } throws IllegalStateException("DB 장애")

        assertFailsWith<IllegalStateException> { useCase.startTrial(100L, "STARTER") }
    }
}
