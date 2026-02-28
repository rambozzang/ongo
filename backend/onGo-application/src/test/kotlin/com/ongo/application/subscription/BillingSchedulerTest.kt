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
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.User
import com.ongo.domain.user.UserRepository
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
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

    private lateinit var billingScheduler: BillingScheduler

    private val now = LocalDateTime.now()

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        billingScheduler = BillingScheduler(
            subscriptionRepository,
            userRepository,
            notificationRepository,
            creditRepository,
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
        pendingPlanType: PlanType? = null,
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
        pendingPlanType = pendingPlanType,
        pausedAt = pausedAt,
        resumeAt = resumeAt,
    )

    private fun stubEmptyDefaults() {
        every { subscriptionRepository.findTrialExpired(any()) } returns emptyList()
        every { subscriptionRepository.findPausedToResume(any()) } returns emptyList()
        every { subscriptionRepository.findPastDue(3) } returns emptyList()
        every { subscriptionRepository.findPastDue(7) } returns emptyList()
        every { subscriptionRepository.findDueForBilling(any()) } returns emptyList()
        every { subscriptionRepository.findWithPendingPlanType() } returns emptyList()
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
        every { subscriptionRepository.findDueForBilling(any()) } returns listOf(sub)
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

        stubEmptyDefaults()
        every { subscriptionRepository.findDueForBilling(any()) } returns listOf(sub)

        // when
        billingScheduler.processBilling()

        // then - update가 호출되지 않아야 함 (filter에서 제외)
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

        val creditSlot = slot<AiCredit>()
        verify { creditRepository.update(capture(creditSlot)) }
        assertEquals(PlanType.STARTER.freeCredits, creditSlot.captured.freeMonthly)

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
}
