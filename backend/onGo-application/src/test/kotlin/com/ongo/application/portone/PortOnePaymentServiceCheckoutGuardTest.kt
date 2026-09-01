package com.ongo.application.portone

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AuthProvider
import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.common.exception.DuplicateSubscriptionPaymentException
import com.ongo.domain.payment.Payment
import com.ongo.application.subscription.DummyTransactionManagerForTest
import com.ongo.application.webhook.WebhookEventRecorder
import com.ongo.application.webhook.WebhookInboundGuard
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
import com.ongo.common.exception.CreditNotFoundException
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
 * - 막는다 — ACTIVE 이고 기간이 남은 유료 구독에 대해 **같거나 더 낮은 등급**의 같은
 *   결제 주기 새 결제
 * - 통과 — 상위 등급(업그레이드). 판정은 `SubscriptionUseCase`와 같은 가격 비교를 쓴다
 * - 통과 — 같은 플랜의 결제 주기 변경. 연간/월간 금액을 실제로 새로 결제한다
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
        /*
         * 정상 사용자는 가입 시 크레딧 원장이 만들어져 있다. 기본 픽스처를 그 상태로 둔다 —
         * 원장이 없는 경우는 아래 전용 테스트가 따로 만든다.
         */
        every { creditService.ensureAccountPresence(userId) } returns Unit
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
            webhookInboundGuard = WebhookInboundGuard(
                webhookEventRepository,
                WebhookEventRecorder(webhookEventRepository, DummyTransactionManagerForTest()),
                DummyTransactionManagerForTest(),
            ),
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
        webhookInboundGuard = WebhookInboundGuard(
            webhookEventRepository,
            WebhookEventRecorder(webhookEventRepository, DummyTransactionManagerForTest()),
            DummyTransactionManagerForTest(),
        ),
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
        billingCycle: BillingCycle = BillingCycle.MONTHLY,
    ) = Subscription(
        id = 1,
        userId = userId,
        planType = planType,
        status = status,
        price = planType.priceFor(billingCycle),
        billingCycle = billingCycle,
        currentPeriodStart = LocalDateTime.now().minusDays(10),
        currentPeriodEnd = periodEnd,
    )

    private fun checkout(plan: PlanType, billingCycle: BillingCycle = BillingCycle.MONTHLY) =
        service.createSubscriptionCheckout(userId, plan.name, billingCycle.name)

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
    @DisplayName("같은 플랜의 연간 전환은 새 연간 결제로 통과한다")
    fun allowsSamePlanYearlySwitch() {
        givenSubscription(subscription(PlanType.STARTER, SubscriptionStatus.ACTIVE))

        val intent = checkout(PlanType.STARTER, BillingCycle.YEARLY)

        assertEquals(PlanType.STARTER.yearlyPrice, intent.amount)
        verify(exactly = 1) { paymentRepository.save(match { it.amount == PlanType.STARTER.yearlyPrice }) }
    }

    @Test
    @DisplayName("하위 플랜의 다른 주기 결제는 즉시 결제하지 않고 예약 API를 사용하게 한다")
    fun rejectsDowngradeWithDifferentCycle() {
        givenSubscription(subscription(PlanType.PRO, SubscriptionStatus.ACTIVE))

        assertThrows(DuplicateSubscriptionPaymentException::class.java) {
            checkout(PlanType.STARTER, BillingCycle.YEARLY)
        }
        verify(exactly = 0) { paymentRepository.save(any()) }
    }

    @Test
    @DisplayName("연간 구독자의 월간 상위 플랜 결제는 과소청구 방지를 위해 거부한다")
    fun rejectsMonthlyUpgradeFromAnnualSubscription() {
        givenSubscription(
            subscription(
                PlanType.STARTER,
                SubscriptionStatus.ACTIVE,
                billingCycle = BillingCycle.YEARLY,
            ),
        )

        val error = assertThrows(BusinessException::class.java) {
            checkout(PlanType.PRO, BillingCycle.MONTHLY)
        }

        assertEquals("PAYMENT_REQUIRED", error.code)
        verify(exactly = 0) { paymentRepository.save(any()) }
    }

    @Test
    @DisplayName("연간 구독자의 같은 플랜 월간 전환은 새 결제 없이 예약 API를 사용하게 한다")
    fun rejectsSamePlanMonthlySwitchFromAnnualSubscription() {
        givenSubscription(
            subscription(
                PlanType.STARTER,
                SubscriptionStatus.ACTIVE,
                billingCycle = BillingCycle.YEARLY,
            ),
        )

        assertThrows(DuplicateSubscriptionPaymentException::class.java) {
            checkout(PlanType.STARTER, BillingCycle.MONTHLY)
        }
        verify(exactly = 0) { paymentRepository.save(any()) }
    }

    @Test
    @DisplayName("FREE 플랜 결제 요청은 기존대로 거부한다")
    fun stillRejectsFreePlanCheckout() {
        assertThrows(IllegalArgumentException::class.java) { checkout(PlanType.FREE) }
        verify(exactly = 0) { subscriptionRepository.findByUserId(any()) }
    }

    // ── 크레딧 원장 부재: 결제창을 열기 전에 막는다 ───────────────────────────
    //
    // 완료 경로(`completeSubscription` → `applyPlanEntitlement`,
    // `completeCredit` → `addPurchasedCredits`)는 원장이 없으면 예외를 던지고, 그 예외가
    // 완료 트랜잭션 전체를 롤백시킨다 — PENDING → COMPLETED 기록까지 함께. 그 시점에는
    // **PG 승인이 이미 끝나 있어** 카드는 빠져나갔는데 우리 쪽 기록이 없는 상태가 되고,
    // 재시도는 매번 같은 지점에서 실패해 웹훅이 DEAD_LETTER 로 떨어진다.
    //
    // 그래서 돈이 움직이기 전에 막는다. 결제 행도 만들지 않는다.

    private fun givenLedgerMissing() {
        every { creditService.ensureAccountPresence(userId) } throws CreditNotFoundException(userId)
    }

    @Test
    @DisplayName("크레딧 원장이 없으면 구독 체크아웃이 결제 행을 만들지 않고 거절한다")
    fun subscriptionCheckoutRejectedWhenLedgerMissing() {
        givenLedgerMissing()

        val error = assertThrows(CreditNotFoundException::class.java) {
            service.createSubscriptionCheckout(userId, "STARTER", "MONTHLY")
        }

        assertEquals("CREDIT_NOT_FOUND", error.code)
        verify(exactly = 0) { paymentRepository.save(any()) }
    }

    @Test
    @DisplayName("크레딧 원장이 없으면 크레딧 체크아웃이 결제 행을 만들지 않고 거절한다")
    fun creditCheckoutRejectedWhenLedgerMissing() {
        givenLedgerMissing()

        val error = assertThrows(CreditNotFoundException::class.java) {
            service.createCreditCheckout(userId, "BASIC")
        }

        assertEquals("CREDIT_NOT_FOUND", error.code)
        verify(exactly = 0) { paymentRepository.save(any()) }
    }

    /**
     * 원장 확인이 **중복 결제 판정보다 먼저** 끝난다.
     *
     * 순서가 뒤집히면 구독 조회를 한 번 더 하게 되고, 무엇보다 유령 구독처럼 다른 이유로
     * 먼저 거절되는 계정에서는 원장 부재가 드러나지 않는다.
     */
    @Test
    @DisplayName("원장이 없으면 구독 조회까지 가지 않는다")
    fun ledgerGuardRunsBeforeDuplicateCheck() {
        givenLedgerMissing()

        assertThrows(CreditNotFoundException::class.java) {
            service.createSubscriptionCheckout(userId, "STARTER", "MONTHLY")
        }

        verify(exactly = 0) { subscriptionRepository.findByUserId(any()) }
    }

    /** 정상 계정은 종전대로 통과한다 — 가드가 과하지 않은지 본다. */
    @Test
    @DisplayName("원장이 있으면 두 체크아웃 모두 종전대로 결제 행을 만든다")
    fun checkoutsStillSucceedWhenLedgerPresent() {
        every { subscriptionRepository.findByUserId(userId) } returns null

        service.createSubscriptionCheckout(userId, "STARTER", "MONTHLY")
        service.createCreditCheckout(userId, "BASIC")

        verify(exactly = 2) { paymentRepository.save(any()) }
        verify(exactly = 2) { creditService.ensureAccountPresence(userId) }
    }
}
