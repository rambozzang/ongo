package com.ongo.application.portone

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.credit.CreditService
import com.ongo.application.subscription.DummyTransactionManagerForTest
import com.ongo.application.webhook.WebhookEventRecorder
import com.ongo.application.webhook.WebhookInboundGuard
import com.ongo.common.enums.AuthProvider
import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.domain.payment.Payment
import com.ongo.domain.payment.PaymentRepository
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.User
import com.ongo.domain.user.UserRepository
import com.ongo.domain.webhook.WebhookEventRepository
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime

/**
 * 결제·환불이 `subscriptions.storage_quota_limit_bytes` 를 어떻게 다루는지 고정한다.
 *
 * ## 이 컬럼의 뜻은 하나다 — 관리자 오버라이드
 *
 * [com.ongo.application.storage.StorageQuotaUseCase.getEffectiveLimit] 은 이 값을 **플랜을
 * 무시하는 절대값**으로 읽는다(`컬럼 ?: plan.storageBytes`). 예전에는 결제가 확정될 때마다
 * 그 플랜의 기본값을 여기 적었고, 두 방향으로 틀렸다.
 *
 *  - 관리자가 CS 로 올려 준 한도가 **다음 결제 한 번에** 사라졌다.
 *  - 어떤 하향 경로도 지우지 않아, FREE 로 내려간 뒤에도 유료 한도가 남았다.
 *
 * 이제 결제는 이 컬럼에 플랜 값을 적지 않는다. 유료 한도는 fallback 이 준다.
 *
 * ## 예외: 새 플랜보다 작은 값은 지운다
 *
 * 이 변경 전에 결제한 계정에는 **그때 플랜의 값**이 남아 있다. 그대로 두면 STARTER(10GB)
 * 사용자가 PRO(50GB) 로 올려도 옛 10GB 에 묶인다 — 돈을 냈는데 산 것을 못 받는, 고치려던
 * 것과 정반대 방향의 결함이다.
 */
@ExtendWith(MockKExtension::class)
class PortOneStorageQuotaOverrideTest {

    @MockK private lateinit var paymentRepository: PaymentRepository
    @MockK private lateinit var subscriptionRepository: SubscriptionRepository
    @MockK private lateinit var userRepository: UserRepository
    @MockK private lateinit var creditService: CreditService
    @MockK private lateinit var gateway: PortOnePaymentGateway
    @MockK private lateinit var webhookEventRepository: WebhookEventRepository

    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private lateinit var service: PortOnePaymentService

    private val userId = 7L
    private val internalPaymentId = 42L
    private val portonePaymentId = "ongo-42"

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
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
            readiness = PortOneReadiness(
                storeId = "store-abc12345",
                channelKey = "channel-abc12345",
                apiSecret = "apisecret-abc12345",
                webhookSecret = "webhook-abc12345",
            ),
            eventPublisher = eventPublisher,
            webhookInboundGuard = WebhookInboundGuard(
                webhookEventRepository,
                WebhookEventRecorder(webhookEventRepository, DummyTransactionManagerForTest()),
                DummyTransactionManagerForTest(),
            ),
        )
        every { userRepository.findById(userId) } returns User(
            id = userId,
            email = "user@test.com",
            name = "tester",
            provider = AuthProvider.GOOGLE,
            providerId = "google-1",
            planType = PlanType.FREE,
        )
        every { userRepository.update(any()) } answers { firstArg() }
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { creditService.applyPlanEntitlement(any(), any(), any()) } just runs

        /*
         * 환불은 **공개 웹훅 경로로만** 들어온다. 테스트 전용 진입점을 운영 코드에 뚫지
         * 않는다 — 그러면 실제로 도는 경로(서명 검증·멱등 게이트·행 잠금)를 건너뛰고
         * 통과하는 테스트가 된다. 단위 테스트에는 경합 상대가 없으므로 잠금 조회는 비어 있다.
         */
        every { gateway.verifyWebhookSignature(any(), any(), any(), any()) } returns true
        every { webhookEventRepository.saveIfAbsent(any()) } returns true
        every { webhookEventRepository.findByEventId(any()) } returns null
        every { webhookEventRepository.findByEventIdForUpdate(any()) } returns null
        every { webhookEventRepository.updateIfNotProcessed(any()) } returns true
        every { webhookEventRepository.markProcessed(any(), any()) } returns true
    }

    private fun cancellationBody() =
        """{"type":"Transaction.Cancelled","data":{"paymentId":"$portonePaymentId","storeId":"store-test"}}"""

    private fun subscriptionPayment(plan: PlanType, status: PaymentStatus) = Payment(
        id = internalPaymentId,
        userId = userId,
        type = PaymentType.SUBSCRIPTION,
        amount = plan.price,
        currency = "KRW",
        status = status,
        pgProvider = "portone",
        description = "SUBSCRIPTION|${plan.name}|MONTHLY",
    )

    private fun subscription(plan: PlanType, overrideBytes: Long?) = Subscription(
        id = 1L,
        userId = userId,
        planType = plan,
        status = SubscriptionStatus.FREE,
        price = plan.price,
        billingCycle = BillingCycle.MONTHLY,
        storageQuotaLimitBytes = overrideBytes,
    )

    private fun paidAtGateway(amount: Int) = PortOnePayment(
        paymentId = portonePaymentId,
        status = "PAID",
        amount = amount,
        currency = "KRW",
        transactionId = "tx-1",
        paymentMethod = "CARD",
        receiptUrl = "https://receipt",
    )

    private fun completePlanPayment(plan: PlanType, existingOverride: Long?): Subscription {
        every { paymentRepository.findByIdForUpdate(internalPaymentId) } returns
            subscriptionPayment(plan, PaymentStatus.PENDING)
        every { paymentRepository.update(any()) } answers { firstArg() }
        every { gateway.getPayment(portonePaymentId) } returns paidAtGateway(plan.price)
        every { subscriptionRepository.findByUserId(userId) } returns
            subscription(PlanType.FREE, existingOverride)

        service.complete(userId, portonePaymentId)

        val captured = slot<Subscription>()
        verify { subscriptionRepository.update(capture(captured)) }
        return captured.captured
    }

    /* ── 결제가 관리자 오버라이드를 덮지 않는다 ─────────────────────── */

    /**
     * **핵심 회귀.** 관리자가 올려 준 한도는 결제 뒤에도 그대로여야 한다.
     *
     * 예전에는 여기서 `plan.storageBytes` 를 적어 넣어, CS 로 부여한 예외 한도가 갱신 결제
     * 한 번에 조용히 사라졌다. 사라졌다는 사실은 어디에도 남지 않는다.
     */
    @Test
    @DisplayName("관리자 오버라이드가 있으면 결제 확정이 그것을 덮지 않는다")
    fun paymentKeepsAdminOverride() {
        val granted = PlanType.BUSINESS.storageBytes * 2

        val saved = completePlanPayment(PlanType.PRO, existingOverride = granted)

        assertEquals(PlanType.PRO, saved.planType)
        assertEquals(granted, saved.storageQuotaLimitBytes, "관리자가 올려 준 한도가 결제로 사라졌다")
    }

    /** 오버라이드가 없으면 결제는 아무것도 적지 않는다 — 한도는 fallback 이 준다. */
    @Test
    @DisplayName("오버라이드가 없으면 결제가 플랜 기본값을 적어 넣지 않는다")
    fun paymentDoesNotWritePlanDefault() {
        val saved = completePlanPayment(PlanType.BUSINESS, existingOverride = null)

        assertEquals(PlanType.BUSINESS, saved.planType)
        assertNull(
            saved.storageQuotaLimitBytes,
            "플랜 기본값을 적으면 하향 뒤에도 남아 결제 없이 유료 한도를 쓰게 된다",
        )
    }

    /**
     * **업그레이드 회귀.** 이 변경 전에 결제한 계정에는 옛 플랜의 값이 남아 있다.
     * 그대로 두면 상향 결제가 옛 한도에 묶인다 — 돈을 냈는데 산 것을 못 받는다.
     */
    @Test
    @DisplayName("새 플랜보다 작은 옛 값은 결제 확정 시 지운다")
    fun paymentClearsStaleLowerValue() {
        val saved = completePlanPayment(PlanType.PRO, existingOverride = PlanType.STARTER.storageBytes)

        assertEquals(PlanType.PRO, saved.planType)
        assertNull(saved.storageQuotaLimitBytes, "옛 STARTER 한도가 남아 PRO 결제가 10GB 에 묶인다")
    }

    /** 경계: 새 플랜 기본값과 정확히 같은 값도 옛 잔재다. 남길 이유가 없다. */
    @Test
    @DisplayName("새 플랜 기본값과 같은 값도 지운다")
    fun paymentClearsValueEqualToPlanDefault() {
        val saved = completePlanPayment(PlanType.PRO, existingOverride = PlanType.PRO.storageBytes)

        assertNull(saved.storageQuotaLimitBytes)
    }

    /* ── 환불 ────────────────────────────────────────────────────────── */

    /**
     * **핵심 회귀.** 환불이면 그 결제로 생긴 저장공간 권한도 함께 거둔다.
     * 남기면 돈은 돌려주고 유료 저장공간은 그대로 쓰게 된다.
     */
    @Test
    @DisplayName("구독 환불이 저장공간 오버라이드를 지운다")
    fun refundClearsOverride() {
        every { paymentRepository.findByIdForUpdate(internalPaymentId) } returns
            subscriptionPayment(PlanType.BUSINESS, PaymentStatus.COMPLETED)
        every { paymentRepository.update(any()) } answers { firstArg() }
        every { gateway.getPayment(portonePaymentId) } returns
            PortOnePayment(
                paymentId = portonePaymentId,
                status = "CANCELLED",
                amount = PlanType.BUSINESS.price,
                currency = "KRW",
                transactionId = "tx-1",
                paymentMethod = "CARD",
                receiptUrl = null,
            )
        every { subscriptionRepository.findByUserId(userId) } returns
            Subscription(
                id = 1L,
                userId = userId,
                planType = PlanType.BUSINESS,
                status = SubscriptionStatus.ACTIVE,
                price = PlanType.BUSINESS.price,
                billingCycle = BillingCycle.MONTHLY,
                currentPeriodEnd = LocalDateTime.now().plusDays(20),
                storageQuotaLimitBytes = PlanType.BUSINESS.storageBytes,
            )

        service.handleWebhook(cancellationBody(), "wh-1", "sig", "ts")

        val captured = slot<Subscription>()
        verify { subscriptionRepository.update(capture(captured)) }
        assertEquals(SubscriptionStatus.CANCELLED, captured.captured.status)
        assertNull(
            captured.captured.storageQuotaLimitBytes,
            "환불했는데 BUSINESS 저장공간이 남았다",
        )
    }
}
