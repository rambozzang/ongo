package com.ongo.application.subscription

import com.ongo.application.portone.PortOnePayment
import com.ongo.application.portone.PortOnePaymentGateway
import com.ongo.application.portone.PortOnePaymentService
import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.notification.NotificationRepository
import com.ongo.domain.payment.Payment
import com.ongo.domain.payment.PaymentRepository
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRenewalAttempt
import com.ongo.domain.subscription.SubscriptionRenewalAttemptRepository
import com.ongo.domain.subscription.SubscriptionRenewalOutcome
import com.ongo.domain.subscription.SubscriptionRepository
import io.mockk.every
import com.ongo.application.credit.CreditService
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 운영자 재확인 경로.
 *
 * `NEEDS_REVIEW` 는 "돈이 움직였을 수 있는데 자동으로 정할 수 없다" 는 상태다. 그래서 이
 * 경로에서 지켜야 할 것은 **바꾸지 않는 쪽이 기본값**이라는 점이다.
 *
 *  1. **절대 청구하지 않는다** — `payWithBillingKey` 는 어떤 분기에서도 호출되지 않는다
 *  2. **PG 가 결말을 낸 두 경우만 전이한다** — 승인+금액·통화 일치, 또는 없음/실패/취소
 *  3. **나머지는 그대로 둔다** — 중간 상태, 금액·통화 불일치, 레거시, 조회 예외
 *  4. **경쟁의 승자는 DB 가 정한다** — 조건부 갱신이 0행이면 정산에 손대지 않는다
 */
class SubscriptionRenewalReviewTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true)
    private val renewalAttemptRepository = mockk<SubscriptionRenewalAttemptRepository>()
    private val notificationRepository = mockk<NotificationRepository>(relaxed = true)
    private val gateway = mockk<PortOnePaymentGateway>()
    private val tokenEncryptionPort = mockk<TokenEncryptionPort>()
    private val paymentRepository = mockk<PaymentRepository>(relaxed = true)
    private val paymentService = mockk<PortOnePaymentService>(relaxed = true)

    /*
     * 이 테스트가 보는 것은 **이미 청구된 주기의 조사·정산**이다. 원장 존재 확인은 청구
     * 앞단의 관심사이므로 여기서는 정상 상태로 두고 종전 의미를 그대로 유지한다.
     * 부재 계약은 `SubscriptionRenewalServiceTest` 가 고정한다.
     */
    private val creditService = mockk<CreditService> {
        every { ensureAccountPresence(any()) } returns Unit
    }

    private val service = SubscriptionRenewalService(
        subscriptionRepository = subscriptionRepository,
        renewalAttemptRepository = renewalAttemptRepository,
        notificationRepository = notificationRepository,
        gateway = gateway,
        tokenEncryptionPort = tokenEncryptionPort,
        paymentRepository = paymentRepository,
        paymentService = paymentService,
        creditService = creditService,
        portoneStoreId = "store-test",
        portoneChannelKey = "channel-test",
        transactionManager = DummyTransactionManagerForTest(),
    )

    private val attemptId = 77L
    private val subscriptionId = 5L
    private val paymentId = 4242L
    private val now = LocalDateTime.parse("2026-09-02T02:00:00")

    private fun attempt(
        outcome: SubscriptionRenewalOutcome = SubscriptionRenewalOutcome.NEEDS_REVIEW,
        linkedPaymentId: Long? = paymentId,
    ) = SubscriptionRenewalAttempt(
        id = attemptId,
        subscriptionId = subscriptionId,
        periodStart = LocalDateTime.parse("2026-09-01T00:00:00"),
        outcome = outcome,
        paymentId = linkedPaymentId,
        createdAt = LocalDateTime.parse("2026-09-01T02:00:00"),
    )

    private fun payment(amount: Int = 19_900, currency: String = "KRW") = Payment(
        id = paymentId,
        userId = 11L,
        type = PaymentType.SUBSCRIPTION,
        amount = amount,
        currency = currency,
        status = PaymentStatus.PENDING,
        pgProvider = "portone",
        description = "SUBSCRIPTION_RENEWAL|PRO|MONTHLY",
    )

    private fun subscription() = Subscription(
        id = subscriptionId,
        userId = 11L,
        planType = PlanType.PRO,
        status = SubscriptionStatus.ACTIVE,
        price = 19_900,
        billingCycle = BillingCycle.MONTHLY,
    )

    private fun portone(status: String, amount: Int = 19_900, currency: String = "KRW") = PortOnePayment(
        paymentId = "ongo-$paymentId",
        status = status,
        amount = amount,
        currency = currency,
        transactionId = "tx-1",
        paymentMethod = "CARD",
        receiptUrl = null,
    )

    private fun given(
        attempt: SubscriptionRenewalAttempt = attempt(),
        payment: Payment? = payment(),
    ) {
        every { renewalAttemptRepository.findById(attemptId) } returns attempt
        every { paymentRepository.findById(paymentId) } returns payment
        every { subscriptionRepository.findById(subscriptionId) } returns subscription()
    }

    /* ---- 절대 하지 않는 것 ---- */

    /**
     * 확인 대상은 이미 돈이 빠져나갔을 수 있는 주기다. 재청구는 그것을 두 번으로 만든다.
     * 어떤 분기로 끝나든 청구 API 는 호출되지 않아야 한다.
     */
    @Test
    fun `어떤 결말에서도 새로 청구하지 않는다`() {
        val statuses = listOf("PAID", "READY", "FAILED", "CANCELLED", "VIRTUAL_ACCOUNT_ISSUED")
        statuses.forEach { status ->
            given()
            every { gateway.findPayment("ongo-$paymentId") } returns portone(status)
            every { renewalAttemptRepository.resolveReviewOutcome(attemptId, any()) } returns true

            service.recheckReview(attemptId, now)
        }

        verify(exactly = 0) { gateway.payWithBillingKey(any()) }
    }

    /* ---- 전이하는 두 경우 ---- */

    @Test
    fun `PG 승인이고 금액과 통화가 같으면 기존 완료 경로로 정산한다`() {
        given()
        every { gateway.findPayment("ongo-$paymentId") } returns portone("PAID")
        every {
            renewalAttemptRepository.resolveReviewOutcome(attemptId, SubscriptionRenewalOutcome.CHARGED)
        } returns true

        val result = service.recheckReview(attemptId, now)

        assertEquals(RenewalReviewDecision.RESOLVED, result.decision)
        assertEquals(SubscriptionRenewalOutcome.CHARGED, result.outcome)
        assertTrue(result.changed)
        // 정산은 우리가 다시 만들지 않고 결제 서비스에 위임한다.
        verify(exactly = 1) { paymentService.complete(null, "ongo-$paymentId") }
        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }

    @Test
    fun `PG 에 결제가 없으면 결제 원장을 닫고 PAST_DUE 로 내린다`() {
        given()
        every { gateway.findPayment("ongo-$paymentId") } returns null
        every {
            renewalAttemptRepository.resolveReviewOutcome(attemptId, SubscriptionRenewalOutcome.CHARGE_FAILED)
        } returns true
        every { paymentRepository.findByIdForUpdate(paymentId) } returns payment()

        val result = service.recheckReview(attemptId, now)

        assertEquals(RenewalReviewDecision.RESOLVED, result.decision)
        assertEquals(SubscriptionRenewalOutcome.CHARGE_FAILED, result.outcome)
        verify { paymentRepository.update(match { it.status == PaymentStatus.FAILED }) }
        verify { subscriptionRepository.update(match { it.status == SubscriptionStatus.PAST_DUE }) }
        verify(exactly = 0) { paymentService.complete(any(), any()) }
    }

    @Test
    fun `PG 가 실패나 취소로 확정했으면 미결제로 닫는다`() {
        listOf("FAILED", "CANCELLED").forEach { status ->
            given()
            every { gateway.findPayment("ongo-$paymentId") } returns portone(status)
            every {
                renewalAttemptRepository.resolveReviewOutcome(attemptId, SubscriptionRenewalOutcome.CHARGE_FAILED)
            } returns true
            every { paymentRepository.findByIdForUpdate(paymentId) } returns payment()

            val result = service.recheckReview(attemptId, now)

            assertEquals(SubscriptionRenewalOutcome.CHARGE_FAILED, result.outcome, "status=$status")
        }
    }

    /**
     * PG 재조회와 확정 사이에 웹훅이 같은 결제를 완료했을 수 있다.
     *
     * 결제·구독을 건드리지 않는 것만으로는 부족하다. **갱신 원장까지 그대로 둬야 한다.**
     * attempt 만 CHARGE_FAILED 로 바뀌면 결제는 성공인데 갱신 원장은 미결제가 되어 두
     * 원장이 갈리고, 그 뒤로는 어느 쪽이 사실인지 판별할 방법이 없다.
     *
     * 확인 대상으로 남기는 것이 정답이다 — 그 주기는 여전히 사람이 봐야 하고, CHARGED 로
     * 올리는 것은 PG 가 PAID 이고 금액·통화가 일치할 때만 하는 일이다.
     */
    @Test
    fun `웹훅이 먼저 완료한 결제는 갱신 원장까지 확인 대상으로 남긴다`() {
        listOf(PaymentStatus.COMPLETED, PaymentStatus.REFUNDED).forEach { settled ->
            given()
            every { gateway.findPayment("ongo-$paymentId") } returns null
            every { paymentRepository.findByIdForUpdate(paymentId) } returns payment().copy(status = settled)

            val result = service.recheckReview(attemptId, now)

            assertEquals(RenewalReviewDecision.STILL_UNDER_REVIEW, result.decision, "status=$settled")
            assertFalse(result.changed, "status=$settled")
            assertNull(result.outcome, "status=$settled")
        }

        // 갱신 원장 전이를 시도조차 하지 않는다.
        verify(exactly = 0) { renewalAttemptRepository.resolveReviewOutcome(any(), any()) }
        verify(exactly = 0) { paymentRepository.update(any()) }
        verify(exactly = 0) { subscriptionRepository.update(any()) }
        verify(exactly = 0) { paymentService.complete(any(), any()) }
    }

    /** 잠근 결과 원장이 없으면 닫을 대상도 없다. 확정하지 않는다. */
    @Test
    fun `잠근 결제 원장이 사라졌으면 확정하지 않는다`() {
        given()
        every { gateway.findPayment("ongo-$paymentId") } returns null
        every { paymentRepository.findByIdForUpdate(paymentId) } returns null

        val result = service.recheckReview(attemptId, now)

        assertEquals(RenewalReviewDecision.MANUAL_ONLY, result.decision)
        verify(exactly = 0) { renewalAttemptRepository.resolveReviewOutcome(any(), any()) }
        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }

    /* ---- 바꾸지 않는 경우 ---- */

    @Test
    fun `금액이 다르면 상태를 바꾸지 않는다`() {
        given()
        every { gateway.findPayment("ongo-$paymentId") } returns portone("PAID", amount = 9_900)

        val result = service.recheckReview(attemptId, now)

        assertEquals(RenewalReviewDecision.STILL_UNDER_REVIEW, result.decision)
        assertFalse(result.changed)
        verify(exactly = 0) { renewalAttemptRepository.resolveReviewOutcome(any(), any()) }
        verify(exactly = 0) { paymentService.complete(any(), any()) }
    }

    @Test
    fun `통화가 다르면 상태를 바꾸지 않는다`() {
        given()
        every { gateway.findPayment("ongo-$paymentId") } returns portone("PAID", currency = "USD")

        val result = service.recheckReview(attemptId, now)

        assertEquals(RenewalReviewDecision.STILL_UNDER_REVIEW, result.decision)
        verify(exactly = 0) { renewalAttemptRepository.resolveReviewOutcome(any(), any()) }
    }

    /** READY 는 곧 승인될 수 있다. 미결제로 확정하면 승인될 결제를 없던 일로 만든다. */
    @Test
    fun `중간 상태는 확정하지 않는다`() {
        listOf("READY", "VIRTUAL_ACCOUNT_ISSUED", "PARTIAL_CANCELLED").forEach { status ->
            given()
            every { gateway.findPayment("ongo-$paymentId") } returns portone(status)

            val result = service.recheckReview(attemptId, now)

            assertEquals(RenewalReviewDecision.STILL_UNDER_REVIEW, result.decision, "status=$status")
        }
        verify(exactly = 0) { renewalAttemptRepository.resolveReviewOutcome(any(), any()) }
    }

    /** 내부 원장이 없으면 `complete` 에 넣을 대상도, 닫을 대상도 없다. 지어내지 않는다. */
    @Test
    fun `내부 원장이 없는 레거시 주기는 조회조차 하지 않고 수기 대사로 남긴다`() {
        every { renewalAttemptRepository.findById(attemptId) } returns attempt(linkedPaymentId = null)

        val result = service.recheckReview(attemptId, now)

        assertEquals(RenewalReviewDecision.MANUAL_ONLY, result.decision)
        assertFalse(result.changed)
        verify(exactly = 0) { gateway.findPayment(any()) }
        verify(exactly = 0) { renewalAttemptRepository.resolveReviewOutcome(any(), any()) }
    }

    /** PG 장애를 "결제 없음" 으로 읽으면 승인된 결제를 미결제로 확정한다. */
    @Test
    fun `조회가 실패하면 예외를 올리고 아무것도 바꾸지 않는다`() {
        given()
        every { gateway.findPayment("ongo-$paymentId") } throws IllegalStateException("PortOne unavailable")

        assertFailsWith<IllegalStateException> { service.recheckReview(attemptId, now) }

        verify(exactly = 0) { renewalAttemptRepository.resolveReviewOutcome(any(), any()) }
        verify(exactly = 0) { paymentService.complete(any(), any()) }
        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }

    @Test
    fun `확인 대상이 아닌 주기는 건드리지 않는다`() {
        every { renewalAttemptRepository.findById(attemptId) } returns
            attempt(outcome = SubscriptionRenewalOutcome.CHARGED)

        val result = service.recheckReview(attemptId, now)

        assertEquals(RenewalReviewDecision.NOT_UNDER_REVIEW, result.decision)
        verify(exactly = 0) { gateway.findPayment(any()) }
    }

    @Test
    fun `없는 기록은 찾을 수 없음으로 끝난다`() {
        every { renewalAttemptRepository.findById(attemptId) } returns null

        val result = service.recheckReview(attemptId, now)

        assertEquals(RenewalReviewDecision.NOT_FOUND, result.decision)
        verify(exactly = 0) { gateway.findPayment(any()) }
    }

    /* ---- 동시 요청 ---- */

    /**
     * 운영자 둘이 같은 건을 눌렀다. 승자는 DB 의 조건부 갱신이 정하고, 진 쪽은 정산에
     * 손대지 않아야 한다. 여기서 두 번 정산하면 기간과 크레딧이 두 번 반영된다.
     */
    @Test
    fun `조건부 갱신에서 지면 정산하지 않는다`() {
        given()
        every { gateway.findPayment("ongo-$paymentId") } returns portone("PAID")
        every { renewalAttemptRepository.resolveReviewOutcome(attemptId, any()) } returns false

        val result = service.recheckReview(attemptId, now)

        assertEquals(RenewalReviewDecision.ALREADY_RESOLVED, result.decision)
        assertFalse(result.changed)
        verify(exactly = 0) { paymentService.complete(any(), any()) }
        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }
}
