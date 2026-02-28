package com.ongo.application.subscription

import com.ongo.application.paddle.PaddleGateway
import com.ongo.application.subscription.dto.ChangePlanRequest
import com.ongo.common.enums.AuthProvider
import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
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

    private lateinit var useCase: SubscriptionUseCase

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        useCase = SubscriptionUseCase(subscriptionRepository, userRepository, videoRepository, paddleGateway)
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

    @Test
    fun `changePlan upgrade should apply immediately with prorated amount`() {
        val subscription = createSubscription(
            planType = PlanType.STARTER,
            currentPeriodEnd = LocalDateTime.now().plusDays(15),
        )
        val user = createUser(planType = PlanType.STARTER)
        val request = ChangePlanRequest(targetPlan = "PRO", billingCycle = "MONTHLY")

        every { subscriptionRepository.findByUserId(100L) } returns subscription
        every { userRepository.findById(100L) } returns user
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { userRepository.update(any()) } answers { firstArg() }

        val response = useCase.changePlan(100L, request)

        assertEquals(PlanType.PRO, response.subscription.planType)
        assertEquals(SubscriptionStatus.ACTIVE, response.subscription.status)
        assertNotNull(response.proratedAmount)
        assert(response.proratedAmount!! >= 0)

        verify { subscriptionRepository.update(match { it.planType == PlanType.PRO }) }
        verify { userRepository.update(match { it.planType == PlanType.PRO }) }
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
    fun `cancelSubscription should set CANCELLED status and cancelledAt`() {
        val subscription = createSubscription()

        every { subscriptionRepository.findByUserId(100L) } returns subscription
        every { subscriptionRepository.update(any()) } answers { firstArg() }

        val response = useCase.cancelSubscription(100L)

        assertEquals(SubscriptionStatus.CANCELLED, response.status)
        verify {
            subscriptionRepository.update(match {
                it.status == SubscriptionStatus.CANCELLED && it.cancelledAt != null
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
        val videos = listOf(
            Video(id = 1L, userId = 100L, title = "v1", fileSizeBytes = 10 * 1024 * 1024L),
            Video(id = 2L, userId = 100L, title = "v2", fileSizeBytes = 20 * 1024 * 1024L),
        )

        every { videoRepository.countByUserIdAndMonth(100L, any<YearMonth>()) } returns 5L
        every { videoRepository.findByUserId(100L, page = 0, size = 10000) } returns videos

        val response = useCase.getUsage(100L)

        assertEquals(5, response.uploadsThisMonth)
        assertEquals(30L, response.storageUsedMb)
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
}
