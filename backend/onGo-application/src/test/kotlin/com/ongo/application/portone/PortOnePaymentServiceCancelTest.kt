package com.ongo.application.portone

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.CreditPackage
import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.domain.payment.Payment
import com.ongo.application.subscription.DummyTransactionManagerForTest
import com.ongo.application.webhook.WebhookEventRecorder
import com.ongo.application.webhook.WebhookInboundGuard
import com.ongo.domain.payment.PaymentRepository
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.UserRepository
import com.ongo.domain.webhook.WebhookEvent
import com.ongo.domain.webhook.WebhookEventRepository
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime

/**
 * 포트원 결제 취소/환불 웹훅 처리 테스트.
 *
 * 정책 (codex와 합의):
 * - 전액 취소(`Transaction.Cancelled` + 포트원 상태 `CANCELLED`)만 결제를 REFUNDED로 바꾸고 크레딧을 회수한다.
 * - 부분 취소(`Transaction.PartialCancelled`)는 이력/로그만 남기고 크레딧·구독·결제 상태를 건드리지 않는다.
 *   금액 비례 회수는 패키지 단위·반올림·재사용 크레딧 때문에 과/과소 회수 위험이 있다.
 * - 구독 결제가 취소되면 status만 CANCELLED로 바꾸고 planType과 기간은 유지한다.
 *   기간 만료 후 FREE 전환은 BillingScheduler.findCancelledExpired가 담당한다.
 * - 웹훅 본문은 신뢰하지 않는다. 항상 포트원 API를 재조회한 상태로 판단한다.
 */
@ExtendWith(MockKExtension::class)
class PortOnePaymentServiceCancelTest {

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

    private lateinit var service: PortOnePaymentService

    private val webhookId = "webhook-cancel-1"
    private val signature = "v1,signature"
    private val timestamp = "1700000000"
    private val portonePaymentId = "ongo-42"

    @MockK
    private lateinit var webhookEventRepository: WebhookEventRepository

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { gateway.verifyWebhookSignature(any(), any(), any(), any()) } returns true
        // 수신 기록은 별도 트랜잭션에서 남고, 업무 처리 전에 행을 잠근다.
        // 단위 테스트에는 경합 상대가 없으므로 잠금 조회는 비어 있다.
        every { webhookEventRepository.saveIfAbsent(any()) } returns true
        every { webhookEventRepository.findByEventId(any()) } returns null andThen
            WebhookEvent(id = 1L, eventId = "portone:$webhookId", eventType = "Transaction.Cancelled", payload = "{}")
        every { webhookEventRepository.findByEventIdForUpdate(any()) } returns null
        every { webhookEventRepository.updateIfNotProcessed(any()) } returns true
        every { webhookEventRepository.markProcessed(any(), any()) } returns true
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
            // 취소·환불 경로는 준비 검사를 지나지 않지만 생성자에 필요하다.
            readiness = PortOneReadiness(
                storeId = "store-abc12345",
                channelKey = "channel-abc12345",
                apiSecret = "apisecret-abc12345",
                webhookSecret = "webhook-abc12345",
            ),
            eventPublisher = mockk(relaxed = true),
            // 수신 기록·행 잠금·실패 기록의 순서는 가드가 정한다. 트랜잭션 경계 자체는
            // WebhookInboundGuardTest 가 고정하므로 여기서는 콜백을 그대로 실행한다.
            webhookInboundGuard = WebhookInboundGuard(
                webhookEventRepository,
                WebhookEventRecorder(webhookEventRepository, DummyTransactionManagerForTest()),
                DummyTransactionManagerForTest(),
            ),
        )
    }

    private fun body(type: String) =
        """{"type":"$type","data":{"paymentId":"$portonePaymentId","storeId":"store-test"}}"""

    private fun creditPayment(status: PaymentStatus = PaymentStatus.COMPLETED) = Payment(
        id = 42,
        userId = 7,
        type = PaymentType.CREDIT,
        amount = CreditPackage.BASIC.price,
        currency = "KRW",
        status = status,
        pgProvider = "portone",
        description = "CREDIT|BASIC",
    )

    private fun subscriptionPayment() = Payment(
        id = 42,
        userId = 7,
        type = PaymentType.SUBSCRIPTION,
        amount = PlanType.PRO.priceFor(BillingCycle.MONTHLY),
        currency = "KRW",
        status = PaymentStatus.COMPLETED,
        pgProvider = "portone",
        description = "SUBSCRIPTION|PRO|MONTHLY",
    )

    private fun portoneStatus(status: String, amount: Int = CreditPackage.BASIC.price) = PortOnePayment(
        paymentId = portonePaymentId,
        status = status,
        amount = amount,
        currency = "KRW",
        transactionId = "tx-1",
        paymentMethod = "CARD",
        receiptUrl = null,
    )

    @Test
    @DisplayName("전액 취소면 결제를 REFUNDED로 바꾸고 크레딧을 회수한다")
    fun fullCancellationRefundsAndRevokesCredits() {
        every { paymentRepository.findByIdForUpdate(42) } returns creditPayment()
        every { gateway.getPayment(portonePaymentId) } returns portoneStatus("CANCELLED")
        val saved = slot<Payment>()
        every { paymentRepository.update(capture(saved)) } answers { saved.captured }
        every { creditService.revokeCredits(7, CreditPackage.BASIC.credits, any()) } just runs

        service.handleWebhook(body("Transaction.Cancelled"), webhookId, signature, timestamp)

        assertEquals(PaymentStatus.REFUNDED, saved.captured.status)
        // 회수량은 금액이 아니라 크레딧 수여야 한다 (Paddle에서 금액을 넘겨 과다 회수한 버그 이력)
        verify(exactly = 1) { creditService.revokeCredits(7, CreditPackage.BASIC.credits, any()) }
    }

    @Test
    @DisplayName("전액 취소는 잠금 조회로 결제를 읽는다 — 동시 처리 방지")
    fun fullCancellationUsesLockingRead() {
        every { paymentRepository.findByIdForUpdate(42) } returns creditPayment()
        every { gateway.getPayment(portonePaymentId) } returns portoneStatus("CANCELLED")
        every { paymentRepository.update(any()) } answers { firstArg() }
        every { creditService.revokeCredits(any(), any(), any()) } just runs

        service.handleWebhook(body("Transaction.Cancelled"), webhookId, signature, timestamp)

        verify(exactly = 1) { paymentRepository.findByIdForUpdate(42) }
        verify(exactly = 0) { paymentRepository.findById(any()) }
    }

    @Test
    @DisplayName("구독 결제 취소는 status만 CANCELLED로 바꾸고 플랜과 기간을 유지한다")
    fun subscriptionCancellationKeepsPlanAndPeriod() {
        val periodEnd = LocalDateTime.now().plusDays(20)
        val subscription = Subscription(
            id = 1,
            userId = 7,
            planType = PlanType.PRO,
            status = SubscriptionStatus.ACTIVE,
            price = PlanType.PRO.priceFor(BillingCycle.MONTHLY),
            billingCycle = BillingCycle.MONTHLY,
            currentPeriodStart = LocalDateTime.now().minusDays(10),
            currentPeriodEnd = periodEnd,
        )
        every { paymentRepository.findByIdForUpdate(42) } returns subscriptionPayment()
        every { gateway.getPayment(portonePaymentId) } returns
            portoneStatus("CANCELLED", PlanType.PRO.priceFor(BillingCycle.MONTHLY))
        every { paymentRepository.update(any()) } answers { firstArg() }
        every { subscriptionRepository.findByUserId(7) } returns subscription
        val savedSub = slot<Subscription>()
        every { subscriptionRepository.update(capture(savedSub)) } answers { savedSub.captured }

        service.handleWebhook(body("Transaction.Cancelled"), webhookId, signature, timestamp)

        assertEquals(SubscriptionStatus.CANCELLED, savedSub.captured.status)
        assertEquals(PlanType.PRO, savedSub.captured.planType, "기간 만료 전에는 플랜을 유지해야 한다")
        assertEquals(periodEnd, savedSub.captured.currentPeriodEnd, "결제 기간을 앞당기면 안 된다")
        // 즉시 FREE 강등은 BillingScheduler.findCancelledExpired 몫이다
        verify(exactly = 0) { userRepository.update(any()) }
    }

    @Test
    @DisplayName("부분 취소는 크레딧을 회수하지 않고 결제 상태도 바꾸지 않는다")
    fun partialCancellationIsRecordedOnly() {
        every { paymentRepository.findByIdForUpdate(42) } returns creditPayment()
        every { gateway.getPayment(portonePaymentId) } returns portoneStatus("PARTIAL_CANCELLED")

        service.handleWebhook(body("Transaction.PartialCancelled"), webhookId, signature, timestamp)

        verify(exactly = 0) { creditService.revokeCredits(any(), any(), any()) }
        verify(exactly = 0) { paymentRepository.update(any()) }
        verify(exactly = 0) { subscriptionRepository.update(any()) }
        // 크레딧을 건드리지 않았을 뿐 웹훅 처리는 성공했다. 이력은 PROCESSED 여야 한다
        verify(exactly = 1) { webhookEventRepository.markProcessed("portone:$webhookId", any()) }
    }

    @Test
    @DisplayName("전액 취소 처리 성공도 이력을 PROCESSED로 남긴다")
    fun fullCancellationMarksProcessed() {
        every { paymentRepository.findByIdForUpdate(42) } returns creditPayment()
        every { gateway.getPayment(portonePaymentId) } returns portoneStatus("CANCELLED")
        every { paymentRepository.update(any()) } answers { firstArg() }
        every { creditService.revokeCredits(any(), any(), any()) } just runs

        service.handleWebhook(body("Transaction.Cancelled"), webhookId, signature, timestamp)

        verify(exactly = 1) { webhookEventRepository.markProcessed("portone:$webhookId", any()) }
    }

    @Test
    @DisplayName("취소 웹훅이어도 포트원 재조회 상태가 PAID면 아무것도 바꾸지 않는다 — 본문 불신")
    fun cancelWebhookWithPaidStatusChangesNothing() {
        every { paymentRepository.findByIdForUpdate(42) } returns creditPayment()
        every { gateway.getPayment(portonePaymentId) } returns portoneStatus("PAID")

        service.handleWebhook(body("Transaction.Cancelled"), webhookId, signature, timestamp)

        verify(exactly = 0) { creditService.revokeCredits(any(), any(), any()) }
        verify(exactly = 0) { paymentRepository.update(any()) }
    }

    @Test
    @DisplayName("이미 REFUNDED인 결제는 재취소해도 크레딧을 다시 회수하지 않는다 — 멱등")
    fun alreadyRefundedIsIdempotent() {
        every { paymentRepository.findByIdForUpdate(42) } returns creditPayment(PaymentStatus.REFUNDED)
        every { gateway.getPayment(portonePaymentId) } returns portoneStatus("CANCELLED")

        service.handleWebhook(body("Transaction.Cancelled"), webhookId, signature, timestamp)

        verify(exactly = 0) { creditService.revokeCredits(any(), any(), any()) }
        verify(exactly = 0) { paymentRepository.update(any()) }
    }

    @Test
    @DisplayName("PENDING 결제의 취소는 FAILED로만 닫고 환불·권한 회수를 하지 않는다")
    fun pendingCancellationDoesNotRefundOrCancelEntitlement() {
        every { paymentRepository.findByIdForUpdate(42) } returns creditPayment(PaymentStatus.PENDING)
        every { gateway.getPayment(portonePaymentId) } returns portoneStatus("CANCELLED")
        val saved = slot<Payment>()
        every { paymentRepository.update(capture(saved)) } answers { saved.captured }

        service.handleWebhook(body("Transaction.Cancelled"), webhookId, signature, timestamp)

        assertEquals(PaymentStatus.FAILED, saved.captured.status)
        verify(exactly = 0) { creditService.revokeCredits(any(), any(), any()) }
        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }

    /**
     * **회수를 건너뛰면서 환불만 커밋하면 금전 누수다.**
     *
     * 고객은 돈을 돌려받고 크레딧은 그대로 쓴다. 게다가 웹훅이 성공 처리되어 `markProcessed`
     * 까지 찍히므로 재전송도, 재시도도, 알림도 없다.
     *
     * 이 상태는 정상 운영에서 나올 수 없다 — `completeCredit` 이 description 파싱에 실패하면
     * 예외를 던지므로 COMPLETED 인 크레딧 결제는 항상 파싱 가능한 description 을 가진다.
     * 따라서 회수량을 추측하지 말고 롤백해 사람이 확인하게 한다.
     */
    @Test
    @DisplayName("크레딧 패키지를 식별할 수 없으면 REFUNDED 도 커밋하지 않는다 — 환불받고 크레딧 유지 방지")
    fun unknownCreditPackageRollsBackTheRefund() {
        every { paymentRepository.findByIdForUpdate(42) } returns
            creditPayment().copy(description = "CREDIT|NONEXISTENT")
        every { gateway.getPayment(portonePaymentId) } returns portoneStatus("CANCELLED")
        every { paymentRepository.update(any()) } answers { firstArg() }

        val error = assertFailsWith<IllegalStateException> {
            service.handleWebhook(body("Transaction.Cancelled"), webhookId, signature, timestamp)
        }

        assertTrue(error.message!!.contains("회수량"), "실패 사유가 회수량 미확정이어야 한다: ${error.message}")
        verify(exactly = 0) { creditService.revokeCredits(any(), any(), any()) }
        // 성공 이력이 남으면 포트원 재전송도 운영 확인도 불가능해진다.
        verify(exactly = 0) { webhookEventRepository.markProcessed(any(), any()) }
    }

    @Test
    @DisplayName("description 자체가 없어도 같은 경로로 막힌다")
    fun missingDescriptionRollsBackTheRefund() {
        every { paymentRepository.findByIdForUpdate(42) } returns creditPayment().copy(description = null)
        every { gateway.getPayment(portonePaymentId) } returns portoneStatus("CANCELLED")
        every { paymentRepository.update(any()) } answers { firstArg() }

        assertFailsWith<IllegalStateException> {
            service.handleWebhook(body("Transaction.Cancelled"), webhookId, signature, timestamp)
        }

        verify(exactly = 0) { creditService.revokeCredits(any(), any(), any()) }
        verify(exactly = 0) { webhookEventRepository.markProcessed(any(), any()) }
    }

    /**
     * 구독 결제가 COMPLETED 라는 것은 결제 시점에 구독 행이 있었다는 뜻이다
     * (`completeSubscription` 이 못 찾으면 `NotFoundException` 을 던진다). 지금 없다면 그 뒤에
     * 사라진 것이고, 조용히 성공 처리하면 **환불은 나가고 `users.planType` 은 유료로 남는다.**
     */
    @Test
    @DisplayName("취소할 구독이 없으면 조용히 성공 처리하지 않는다 — 환불 후 유료 권한 잔존 방지")
    fun missingSubscriptionRollsBackTheRefund() {
        every { paymentRepository.findByIdForUpdate(42) } returns subscriptionPayment()
        every { gateway.getPayment(portonePaymentId) } returns
            portoneStatus("CANCELLED", PlanType.PRO.priceFor(BillingCycle.MONTHLY))
        every { paymentRepository.update(any()) } answers { firstArg() }
        every { subscriptionRepository.findByUserId(7) } returns null

        val error = assertFailsWith<IllegalStateException> {
            service.handleWebhook(body("Transaction.Cancelled"), webhookId, signature, timestamp)
        }

        assertTrue(error.message!!.contains("구독"), "실패 사유가 다르다: ${error.message}")
        verify(exactly = 0) { subscriptionRepository.update(any()) }
        verify(exactly = 0) { webhookEventRepository.markProcessed(any(), any()) }
    }
}
