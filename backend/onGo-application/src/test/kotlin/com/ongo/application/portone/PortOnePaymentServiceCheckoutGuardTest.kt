package com.ongo.application.portone

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AuthProvider
import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.common.exception.DuplicateSubscriptionPaymentException
import com.ongo.domain.payment.Payment
import com.ongo.domain.payment.PaymentRepository
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.User
import com.ongo.domain.user.UserRepository
import com.ongo.domain.webhook.WebhookEventRepository
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import com.ongo.common.exception.BusinessException
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime

/**
 * 구독 결제 intent 생성의 중복 청구 가드.
 *
 * 배경: 온보딩에서 결제를 끝낸 뒤 '이전'으로 3단계에 돌아가 '다음'을 누르면 같은 구독을 다시
 * 결제할 수 있었다. `complete()`의 멱등성은 paymentId 단위라 새 체크아웃은 별건으로 통과하고
 * 카드가 두 번 청구된다. 프론트 상태만으로는 새로고침·직접 API 호출을 못 막으므로 서버에서 닫는다.
 *
 * 정책:
 * - 막는다 — ACTIVE 이고 기간이 남은 유료 구독에 대해 **같거나 더 낮은 등급**의 새 결제
 * - 통과 — 상위 등급(업그레이드). 판정은 `SubscriptionUseCase`와 같은 가격 비교를 쓴다
 * - 통과 — PAST_DUE 재결제 / CANCELLED 재가입 / TRIALING 유료 전환 / 기간 만료 갱신 / FREE 첫 결제
 */
@ExtendWith(MockKExtension::class)
@DisplayName("PortOne 구독 체크아웃 중복 결제 가드")
class PortOnePaymentServiceCheckoutGuardTest {

    @MockK
    private lateinit var paymentRepository: PaymentRepository

    @MockK
    private lateinit var subscriptionRepository: SubscriptionRepository

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var creditService: CreditService

    @MockK
    private lateinit var gateway: PortOnePaymentGateway

    @MockK
    private lateinit var webhookEventRepository: WebhookEventRepository

    private lateinit var service: PortOnePaymentService

    private val userId = 7L

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { userRepository.findById(userId) } returns User(
            id = userId,
            email = "creator@example.com",
            name = "크리에이터",
            provider = AuthProvider.GOOGLE,
            providerId = "google-7",
        )
        // 통과 경로에서만 호출된다. 저장된 Payment 에 id 를 붙여 돌려준다.
        every { paymentRepository.save(any()) } answers { firstArg<Payment>().copy(id = 100) }
        service = PortOnePaymentService(
            paymentRepository = paymentRepository,
            subscriptionRepository = subscriptionRepository,
            userRepository = userRepository,
            creditService = creditService,
            gateway = gateway,
            webhookEventRepository = webhookEventRepository,
            objectMapper = ObjectMapper(),
            storeId = "store-test",
            channelKey = "channel-test",
            readiness = readyReadiness(),
            eventPublisher = mockk(relaxed = true),
        )
    }

    /** 설정이 준비된 상태. 준비되지 않은 경우는 아래 전용 테스트가 따로 만든다. */
    private fun readyReadiness() = PortOneReadiness(
        storeId = "store-abc12345",
        channelKey = "channel-abc12345",
        apiSecret = "apisecret-abc12345",
        webhookSecret = "webhook-abc12345",
    )

    /** 설정이 준비되지 않은 서비스. 저장소 목은 그대로 공유해 save 호출 0회를 검증한다. */
    private fun serviceWithUnreadyPayment() = PortOnePaymentService(
        paymentRepository = paymentRepository,
        subscriptionRepository = subscriptionRepository,
        userRepository = userRepository,
        creditService = creditService,
        gateway = gateway,
        webhookEventRepository = webhookEventRepository,
        objectMapper = ObjectMapper(),
        storeId = "store-test",
        channelKey = "channel-test",
        // webhook secret 이 비면 서명 검증을 못 하므로 결제를 시작하면 안 된다.
        readiness = PortOneReadiness(
            storeId = "store-abc12345",
            channelKey = "channel-abc12345",
            apiSecret = "apisecret-abc12345",
            webhookSecret = "",
        ),
        // 체크아웃 단계는 결제 확정이 아니라 이벤트를 내지 않는다. 그래도 생성자에는 필요하다.
        eventPublisher = mockk(relaxed = true),
    )

    /*
     * 설정이 비어 있으면 프론트는 빈 storeId 로 SDK 를 열어 원문 오류를 띄웠고, DB 에는
     * 아무도 정리하지 않는 PENDING 행이 남았다. 그래서 **행을 만들기 전에** 막는다.
     */
    @Test
    fun `결제 설정이 준비되지 않으면 구독 체크아웃이 행을 만들지 않고 거절한다`() {
        val ex = assertFailsWith<BusinessException> {
            serviceWithUnreadyPayment().createSubscriptionCheckout(1L, "STARTER", "MONTHLY")
        }

        assertEquals("PAYMENT_NOT_AVAILABLE", ex.code)
        verify(exactly = 0) { paymentRepository.save(any()) }
        // 어느 설정이 빠졌는지 사용자에게 알리지 않는다.
        assertFalse(ex.message.orEmpty().contains("secret"))
        assertFalse(ex.message.orEmpty().contains("channel"))
    }

    @Test
    fun `결제 설정이 준비되지 않으면 크레딧 체크아웃이 행을 만들지 않고 거절한다`() {
        val ex = assertFailsWith<BusinessException> {
            serviceWithUnreadyPayment().createCreditCheckout(1L, "BASIC")
        }

        assertEquals("PAYMENT_NOT_AVAILABLE", ex.code)
        verify(exactly = 0) { paymentRepository.save(any()) }
    }

    /* 준비 검사는 사용자 조회보다 먼저다 — 없는 사용자로도 설정 상태가 새면 안 된다. */
    @Test
    fun `준비되지 않으면 사용자 조회도 하지 않는다`() {
        assertFailsWith<BusinessException> {
            serviceWithUnreadyPayment().createCreditCheckout(999L, "BASIC")
        }

        verify(exactly = 0) { userRepository.findById(999L) }
    }

    private fun subscription(
        planType: PlanType,
        status: SubscriptionStatus,
        periodEnd: LocalDateTime? = LocalDateTime.now().plusDays(20),
    ) = Subscription(
        id = 1,
        userId = userId,
        planType = planType,
        status = status,
        price = planType.price,
        billingCycle = BillingCycle.MONTHLY,
        currentPeriodStart = LocalDateTime.now().minusDays(10),
        currentPeriodEnd = periodEnd,
    )

    private fun checkout(plan: PlanType) =
        service.createSubscriptionCheckout(userId, plan.name, BillingCycle.MONTHLY.name)

    private fun givenSubscription(subscription: Subscription?) {
        every { subscriptionRepository.findByUserId(userId) } returns subscription
    }

    @Test
    @DisplayName("이용 중인 플랜과 같은 등급의 결제는 거부한다 — 온보딩 재결제 경로")
    fun rejectsSamePlanRepurchase() {
        givenSubscription(subscription(PlanType.STARTER, SubscriptionStatus.ACTIVE))

        val e = assertThrows(DuplicateSubscriptionPaymentException::class.java) {
            checkout(PlanType.STARTER)
        }

        assertEquals("SUBSCRIPTION_ALREADY_ACTIVE", e.code)
        assertEquals(PlanType.STARTER, e.currentPlan)
        assertEquals(PlanType.STARTER, e.requestedPlan)
        // 결제 레코드를 만들지 않아야 한다. 만들면 그대로 청구로 이어진다.
        verify(exactly = 0) { paymentRepository.save(any()) }
    }

    @Test
    @DisplayName("이용 중인 플랜보다 낮은 등급의 결제도 거부한다")
    fun rejectsDowngradePayment() {
        givenSubscription(subscription(PlanType.PRO, SubscriptionStatus.ACTIVE))

        val e = assertThrows(DuplicateSubscriptionPaymentException::class.java) {
            checkout(PlanType.STARTER)
        }

        assertEquals(PlanType.PRO, e.currentPlan)
        verify(exactly = 0) { paymentRepository.save(any()) }
    }

    @Test
    @DisplayName("상위 등급 업그레이드는 그대로 통과한다 — 구독 화면의 정상 흐름")
    fun allowsUpgrade() {
        givenSubscription(subscription(PlanType.STARTER, SubscriptionStatus.ACTIVE))

        val intent = checkout(PlanType.PRO)

        assertEquals(PlanType.PRO.price, intent.amount)
        verify(exactly = 1) { paymentRepository.save(any()) }
    }

    @Test
    @DisplayName("FREE 구독자의 첫 유료 결제는 통과한다")
    fun allowsFirstPaidCheckout() {
        givenSubscription(subscription(PlanType.FREE, SubscriptionStatus.ACTIVE, periodEnd = null))

        val intent = checkout(PlanType.STARTER)

        assertEquals(PlanType.STARTER.price, intent.amount)
    }

    @Test
    @DisplayName("구독 레코드가 없으면 통과한다")
    fun allowsWhenNoSubscriptionRow() {
        givenSubscription(null)

        val intent = checkout(PlanType.STARTER)

        assertEquals(PlanType.STARTER.price, intent.amount)
    }

    @Test
    @DisplayName("트라이얼 중 같은 유료 플랜 전환은 통과한다")
    fun allowsTrialConversion() {
        givenSubscription(subscription(PlanType.PRO, SubscriptionStatus.TRIALING))

        val intent = checkout(PlanType.PRO)

        assertEquals(PlanType.PRO.price, intent.amount)
    }

    @Test
    @DisplayName("결제 실패(PAST_DUE) 상태의 같은 플랜 재결제는 통과한다")
    fun allowsPastDueRetry() {
        givenSubscription(subscription(PlanType.STARTER, SubscriptionStatus.PAST_DUE))

        val intent = checkout(PlanType.STARTER)

        assertEquals(PlanType.STARTER.price, intent.amount)
    }

    @Test
    @DisplayName("해지한 구독의 같은 플랜 재가입은 통과한다")
    fun allowsResubscribeAfterCancel() {
        givenSubscription(subscription(PlanType.STARTER, SubscriptionStatus.CANCELLED))

        val intent = checkout(PlanType.STARTER)

        assertEquals(PlanType.STARTER.price, intent.amount)
    }

    @Test
    @DisplayName("결제 기간이 끝난 구독의 같은 플랜 갱신은 통과한다")
    fun allowsRenewalAfterPeriodEnd() {
        givenSubscription(
            subscription(
                PlanType.STARTER,
                SubscriptionStatus.ACTIVE,
                periodEnd = LocalDateTime.now().minusDays(1),
            )
        )

        val intent = checkout(PlanType.STARTER)

        assertEquals(PlanType.STARTER.price, intent.amount)
    }

    @Test
    @DisplayName("연간 결제로 올리는 것도 같은 등급이면 거부한다 — 주기 변경은 플랜 변경 API 를 쓴다")
    fun rejectsSamePlanYearlySwitch() {
        givenSubscription(subscription(PlanType.STARTER, SubscriptionStatus.ACTIVE))

        assertThrows(DuplicateSubscriptionPaymentException::class.java) {
            service.createSubscriptionCheckout(userId, PlanType.STARTER.name, BillingCycle.YEARLY.name)
        }
        verify(exactly = 0) { paymentRepository.save(any()) }
    }

    @Test
    @DisplayName("FREE 플랜 결제 요청은 기존대로 거부한다")
    fun stillRejectsFreePlanCheckout() {
        assertThrows(IllegalArgumentException::class.java) { checkout(PlanType.FREE) }
        verify(exactly = 0) { subscriptionRepository.findByUserId(any()) }
    }
}
