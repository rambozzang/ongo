package com.ongo.application.payment

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.credit.CreditService
import com.ongo.application.payment.dto.TossReceipt
import com.ongo.application.payment.dto.TossWebhookPayload
import com.ongo.application.webhook.WebhookInboundGuard
import com.ongo.application.webhook.WebhookInboundOutcome
import com.ongo.common.enums.CreditPackage
import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.common.exception.UnauthorizedException
import com.ongo.domain.payment.Payment
import com.ongo.domain.payment.PaymentRepository
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 레거시 Toss 웹훅이 **PortOne·Paddle 과 같은 계약**을 지키는지 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * 신규 결제는 전부 PortOne 이지만 이 엔드포인트는 열려 있고, 형제 두 경로가 각각 고친
 * 결함이 여기만 남아 있었다.
 *
 * - 크레딧 패키지를 식별하지 못하면 `log.error` 후 **계속 진행**했다. 바로 앞에서
 *   `COMPLETED` 로 갱신한 결제가 커밋돼, 돈을 낸 사용자에게 **크레딧 0 인 "결제 완료"**
 *   만 남았다. 200 을 돌려주니 Toss 도 재시도하지 않는다.
 * - `findById` 라 행 잠금이 없어 동시 `DONE` 두 건이 모두 통과 → **크레딧 이중 지급**.
 * - 환불 뒤 늦게 온 `DONE` 이 `REFUNDED` 를 `COMPLETED` 로 되살렸다.
 * - `webhook_events` 기록이 없어 중복 탐지·재처리·데드레터가 불가능했다.
 *
 * ## 가드를 진짜로 통과시킨다
 *
 * [WebhookInboundGuard] 를 목으로 두되 **람다를 실제로 실행**한다. relaxed 목으로 두면
 * 업무 로직이 한 번도 돌지 않은 채 모든 단언이 통과한다.
 */
class TossWebhookHardeningTest {

    private val paymentRepository = mockk<PaymentRepository>()
    private val creditService = mockk<CreditService>(relaxed = true)
    private val subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true)
    private val guard = mockk<WebhookInboundGuard>()
    private val secret = "toss-webhook-secret"

    private val service = PaymentService(
        paymentRepository = paymentRepository,
        creditService = creditService,
        subscriptionRepository = subscriptionRepository,
        webhookInboundGuard = guard,
        objectMapper = ObjectMapper(),
        tossWebhookSecret = secret,
    )

    /** 가드가 받은 eventId. 멱등 키 계약을 직접 본다. */
    private val capturedEventId = slot<String>()
    private val capturedEventType = slot<String>()

    /** 가드는 통과시키고 업무 람다를 실행한다 — 그래야 아래 단언이 무언가를 검사한다. */
    private fun guardRunsBusiness(outcome: WebhookInboundOutcome = WebhookInboundOutcome.PROCESSED) {
        every {
            guard.handle(capture(capturedEventId), capture(capturedEventType), any(), any())
        } answers {
            @Suppress("UNCHECKED_CAST")
            (arg<Any>(3) as () -> Unit).invoke()
            outcome
        }
    }

    private fun payload(
        status: String = "DONE",
        orderId: String = "ongo-42",
        totalAmount: Int = CreditPackage.entries.first().price,
    ) = TossWebhookPayload(
        paymentKey = "toss-key",
        orderId = orderId,
        status = status,
        totalAmount = totalAmount,
        method = "카드",
        approvedAt = null,
        receipt = TossReceipt(url = "https://receipt.example/1"),
    )

    private fun sign(p: TossWebhookPayload): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder()
            .encodeToString(mac.doFinal((p.orderId + p.status + p.totalAmount).toByteArray()))
    }

    private fun payment(
        status: PaymentStatus = PaymentStatus.PENDING,
        type: PaymentType = PaymentType.CREDIT,
        amount: Int = CreditPackage.entries.first().price,
    ) = Payment(
        id = 42L,
        userId = 7L,
        type = type,
        amount = amount,
        status = status,
        description = "테스트 결제",
    )

    private fun handle(p: TossWebhookPayload = payload()) = service.handleWebhook(p, sign(p))

    // ── 서명 (기존 계약 유지) ────────────────────────────────────────────────

    @Test
    @DisplayName("서명이 없거나 틀리면 업무 처리를 시작하지 않는다")
    fun signatureIsStillRequired() {
        assertThrows<UnauthorizedException> { service.handleWebhook(payload(), null) }
        assertThrows<UnauthorizedException> { service.handleWebhook(payload(), "wrong") }

        verify(exactly = 0) { guard.handle(any(), any(), any(), any()) }
    }

    // ── 멱등 게이트 ──────────────────────────────────────────────────────────

    /** **이 경로에는 멱등 장치가 아예 없었다.** */
    @Test
    @DisplayName("웹훅은 멱등 가드를 통과해서만 반영된다")
    fun webhookGoesThroughTheInboundGuard() {
        guardRunsBusiness()
        every { paymentRepository.findByIdForUpdate(42L) } returns payment()
        every { paymentRepository.update(any()) } answers { firstArg() }

        handle()

        verify(exactly = 1) { guard.handle(any(), any(), any(), any()) }
    }

    /**
     * Toss 에는 `event_id` 헤더가 없다. 키는 **서명이 덮는 세 필드**로만 만들어야
     * 키의 신뢰도가 서명과 같아진다.
     */
    @Test
    @DisplayName("멱등 키는 주문·상태·금액으로 결정된다")
    fun eventKeyIsDeterministicFromSignedFields() {
        guardRunsBusiness()
        every { paymentRepository.findByIdForUpdate(42L) } returns payment()
        every { paymentRepository.update(any()) } answers { firstArg() }

        val p = payload()
        handle(p)

        assertEquals("toss:${p.orderId}:${p.status}:${p.totalAmount}", capturedEventId.captured)
        assertTrue(capturedEventType.captured.contains(p.status), capturedEventType.captured)
    }

    /** 상태가 다르면 키도 달라야 취소가 승인의 중복으로 묻히지 않는다. */
    @Test
    @DisplayName("상태가 다르면 멱등 키도 다르다")
    fun differentStatusYieldsDifferentKey() {
        guardRunsBusiness()
        every { paymentRepository.findByIdForUpdate(42L) } returns payment()
        every { paymentRepository.update(any()) } answers { firstArg() }

        handle(payload(status = "DONE"))
        val doneKey = capturedEventId.captured

        every { paymentRepository.findByIdForUpdate(42L) } returns payment(status = PaymentStatus.COMPLETED)
        handle(payload(status = "CANCELED"))

        assertTrue(doneKey != capturedEventId.captured, "승인과 취소가 같은 키를 썼다")
    }

    /** 가드가 중복이라고 판정하면 업무 람다가 아예 실행되지 않는다. */
    @Test
    @DisplayName("가드가 중복으로 판정하면 크레딧을 지급하지 않는다")
    fun duplicateDeliveryGrantsNothing() {
        every { guard.handle(any(), any(), any(), any()) } returns WebhookInboundOutcome.ALREADY_PROCESSED

        handle()

        verify(exactly = 0) { creditService.addPurchasedCredits(any(), any(), any()) }
        verify(exactly = 0) { paymentRepository.update(any()) }
    }

    /** 같은 상태가 다시 오면 아무것도 바꾸지 않는다(가드를 통과한 경우에도). */
    @Test
    @DisplayName("이미 같은 상태면 재지급하지 않는다")
    fun sameStatusIsANoOp() {
        guardRunsBusiness()
        every { paymentRepository.findByIdForUpdate(42L) } returns payment(status = PaymentStatus.COMPLETED)

        handle()

        verify(exactly = 0) { creditService.addPurchasedCredits(any(), any(), any()) }
        verify(exactly = 0) { paymentRepository.update(any()) }
    }

    // ── 동시성: 행 잠금 ──────────────────────────────────────────────────────

    /**
     * **잠금 없이 읽으면 동시 전달 둘이 모두 이전 상태를 보고 통과한다.**
     *
     * 목으로 동시성 자체를 재현할 수는 없으므로, 잠금 조회를 쓰는지를 고정한다 —
     * PortOne 이 같은 이유로 `findByIdForUpdate` 를 쓴다.
     */
    @Test
    @DisplayName("결제를 행 잠금으로 읽는다")
    fun paymentIsReadWithRowLock() {
        guardRunsBusiness()
        every { paymentRepository.findByIdForUpdate(42L) } returns payment()
        every { paymentRepository.update(any()) } answers { firstArg() }

        handle()

        verify(exactly = 1) { paymentRepository.findByIdForUpdate(42L) }
        verify(exactly = 0) { paymentRepository.findById(any()) }
    }

    // ── 상태 역전 ────────────────────────────────────────────────────────────

    /** **환불 뒤 늦게 온 승인이 권한을 되살리면 안 된다.** */
    @Test
    @DisplayName("환불된 결제는 늦은 DONE 으로 되살아나지 않는다")
    fun refundedPaymentIsNotResurrectedByLateDone() {
        guardRunsBusiness()
        every { paymentRepository.findByIdForUpdate(42L) } returns payment(status = PaymentStatus.REFUNDED)

        val error = assertThrows<IllegalStateException> { handle() }

        assertTrue("환불" in error.message!!, error.message!!)
        verify(exactly = 0) { paymentRepository.update(any()) }
        verify(exactly = 0) { creditService.addPurchasedCredits(any(), any(), any()) }
    }

    // ── 크레딧 패키지 미식별 ─────────────────────────────────────────────────

    /**
     * **이 케이스가 "완료인데 크레딧 0" 을 만들던 자리다.**
     *
     * 예외를 던져야 가드의 업무 트랜잭션이 롤백되고 재시도·데드레터가 작동한다.
     */
    @Test
    @DisplayName("크레딧 패키지를 식별할 수 없으면 완료로 만들지 않고 던진다")
    fun unknownCreditPackageThrowsBeforeCompleting() {
        val oddAmount = CreditPackage.entries.maxOf { it.price } + 7
        guardRunsBusiness()
        every { paymentRepository.findByIdForUpdate(42L) } returns payment(amount = oddAmount)

        val error = assertThrows<IllegalStateException> {
            handle(payload(totalAmount = oddAmount))
        }

        assertTrue("크레딧 패키지" in error.message!!, error.message!!)
        // 상태 갱신도 지급도 일어나지 않아야 롤백 후 재시도가 의미를 갖는다.
        verify(exactly = 0) { paymentRepository.update(any()) }
        verify(exactly = 0) { creditService.addPurchasedCredits(any(), any(), any()) }
    }

    /** 구독 결제는 패키지 역산 대상이 아니므로 금액이 달라도 정상 처리된다. */
    @Test
    @DisplayName("구독 결제는 크레딧 패키지를 요구하지 않는다")
    fun subscriptionPaymentDoesNotRequireACreditPackage() {
        val oddAmount = CreditPackage.entries.maxOf { it.price } + 7
        guardRunsBusiness()
        every {
            paymentRepository.findByIdForUpdate(42L)
        } returns payment(type = PaymentType.SUBSCRIPTION, amount = oddAmount)
        every { paymentRepository.update(any()) } answers { firstArg() }

        handle(payload(totalAmount = oddAmount))

        verify(exactly = 1) { paymentRepository.update(any()) }
        verify(exactly = 0) { creditService.addPurchasedCredits(any(), any(), any()) }
    }

    // ── 잘못된 입력은 조용히 넘기지 않는다 ───────────────────────────────────

    /** 예전에는 `?: return` 이라 200 이 나가고 아무도 모르게 끝났다. */
    @Test
    @DisplayName("주문 번호에서 결제 식별자를 못 읽으면 던진다")
    fun malformedOrderIdThrows() {
        guardRunsBusiness()

        val error = assertThrows<IllegalStateException> { handle(payload(orderId = "쓰레기")) }

        assertTrue("주문 번호" in error.message!!, error.message!!)
        verify(exactly = 0) { paymentRepository.update(any()) }
    }

    @Test
    @DisplayName("대응하는 결제가 없으면 던진다")
    fun missingPaymentThrows() {
        guardRunsBusiness()
        every { paymentRepository.findByIdForUpdate(42L) } returns null

        val error = assertThrows<IllegalStateException> { handle() }

        assertTrue("찾을 수 없" in error.message!!, error.message!!)
    }

    /** 금액 불일치는 기존 계약 그대로 거절한다. */
    @Test
    @DisplayName("금액이 주문과 다르면 거절한다")
    fun amountMismatchIsRejected() {
        guardRunsBusiness()
        every { paymentRepository.findByIdForUpdate(42L) } returns payment(amount = 1)

        assertThrows<IllegalArgumentException> { handle() }

        verify(exactly = 0) { paymentRepository.update(any()) }
    }

    // ── 정상 경로 보존 ───────────────────────────────────────────────────────

    /** 과도한 차단 회귀를 막는다. */
    @Test
    @DisplayName("정상 DONE 은 크레딧을 지급하고 완료로 남긴다")
    fun normalDoneGrantsCredits() {
        val pkg = CreditPackage.entries.first()
        guardRunsBusiness()
        every { paymentRepository.findByIdForUpdate(42L) } returns payment(amount = pkg.price)
        val saved = slot<Payment>()
        every { paymentRepository.update(capture(saved)) } answers { firstArg() }

        handle(payload(totalAmount = pkg.price))

        assertEquals(PaymentStatus.COMPLETED, saved.captured.status)
        assertEquals("toss", saved.captured.pgProvider)
        verify(exactly = 1) { creditService.addPurchasedCredits(7L, pkg, 42L) }
    }

    /** 취소는 크레딧을 회수한다. */
    @Test
    @DisplayName("CANCELED 는 환불로 남기고 크레딧을 회수한다")
    fun canceledRevokesCredits() {
        val pkg = CreditPackage.entries.first()
        guardRunsBusiness()
        every {
            paymentRepository.findByIdForUpdate(42L)
        } returns payment(status = PaymentStatus.COMPLETED, amount = pkg.price)
        val saved = slot<Payment>()
        every { paymentRepository.update(capture(saved)) } answers { firstArg() }

        handle(payload(status = "CANCELED", totalAmount = pkg.price))

        assertEquals(PaymentStatus.REFUNDED, saved.captured.status)
        verify(exactly = 1) { creditService.revokeCredits(7L, pkg.credits, "PAYMENT_REFUND:42") }
    }

    // ── 구독 환불 (P1) ───────────────────────────────────────────────────────
    //
    // 이 경로는 `PaymentType.CREDIT` 환불만 처리하고 구독은 손대지 않았다. 레거시 Toss
    // 구독 결제가 환불되면 **돈은 돌려주고 유료 권한은 그대로 남았다.**
    //
    // 계약은 PortOne 전액취소와 같다 — `CANCELLED` 로 표시하고 `cancelledAt` 을 남기며
    // 예약 필드를 비운다. `planType` 은 여기서 내리지 않는다(만료 설계가 소유).

    private val subscriptionAmount = CreditPackage.entries.maxOf { it.price } + 11

    private fun subscription(
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        planType: PlanType = PlanType.PRO,
    ) = Subscription(
        id = 3L,
        userId = 7L,
        planType = planType,
        status = status,
        pendingPlanType = PlanType.STARTER,
        pendingBillingCycle = com.ongo.common.enums.BillingCycle.YEARLY,
    )

    private fun givenSubscriptionRefund(
        paymentStatus: PaymentStatus = PaymentStatus.COMPLETED,
        existing: Subscription? = subscription(),
    ): io.mockk.CapturingSlot<Subscription> {
        guardRunsBusiness()
        every {
            paymentRepository.findByIdForUpdate(42L)
        } returns payment(status = paymentStatus, type = PaymentType.SUBSCRIPTION, amount = subscriptionAmount)
        every { paymentRepository.update(any()) } answers { firstArg() }
        every { subscriptionRepository.findByUserId(7L) } returns existing
        val saved = slot<Subscription>()
        every { subscriptionRepository.update(capture(saved)) } answers { firstArg() }
        return saved
    }

    private fun refundPayload() = payload(status = "CANCELED", totalAmount = subscriptionAmount)

    /** **이 케이스가 "환불했는데 유료 권한 유지" 를 만들던 자리다.** */
    @Test
    @DisplayName("구독 결제 환불은 구독을 CANCELLED 로 해제한다")
    fun subscriptionRefundCancelsSubscription() {
        val saved = givenSubscriptionRefund()

        handle(refundPayload())

        assertEquals(SubscriptionStatus.CANCELLED, saved.captured.status)
        assertTrue(saved.captured.cancelledAt != null, "cancelledAt 을 남기지 않았다")
    }

    /** 예약된 플랜/주기가 남아 있으면 해제 후에도 그 변경이 적용된다. */
    @Test
    @DisplayName("해제 시 예약된 플랜·주기를 비운다")
    fun subscriptionRefundClearsPendingFields() {
        val saved = givenSubscriptionRefund()

        handle(refundPayload())

        assertEquals(null, saved.captured.pendingPlanType)
        assertEquals(null, saved.captured.pendingBillingCycle)
    }

    /**
     * **planType 은 여기서 내리지 않는다.**
     *
     * 강등은 `BillingScheduler.downgradeCancelled` 가 `findCancelledExpired`
     * (`status = CANCELLED AND current_period_end < now`)로 골라 수행한다. 여기서 앞당겨
     * 내리면 기간이 남은 구독의 권한을 조기에 끊는다. PortOne 도 같은 계약이다.
     */
    @Test
    @DisplayName("해제만 하고 planType 은 그대로 둔다")
    fun subscriptionRefundDoesNotDowngradePlanType() {
        val saved = givenSubscriptionRefund()

        handle(refundPayload())

        assertEquals(PlanType.PRO, saved.captured.planType, "만료 설계가 할 강등을 앞당겼다")
    }

    /** 구독 결제 환불은 크레딧을 건드리지 않는다. */
    @Test
    @DisplayName("구독 환불은 크레딧을 회수하지 않는다")
    fun subscriptionRefundDoesNotTouchCredits() {
        givenSubscriptionRefund()

        handle(refundPayload())

        verify(exactly = 0) { creditService.revokeCredits(any(), any(), any()) }
    }

    /**
     * **결제된 적 없는 행의 취소로 구독을 끊지 않는다.**
     *
     * PortOne 도 환불을 `COMPLETED` 에만 적용한다. PENDING 취소로 해제하면 돈을 낸 적
     * 없는 사유로 사용 중인 구독이 끊긴다.
     */
    @Test
    @DisplayName("승인된 적 없는 결제의 취소는 구독을 해제하지 않는다")
    fun pendingCancellationDoesNotCancelSubscription() {
        givenSubscriptionRefund(paymentStatus = PaymentStatus.PENDING)

        handle(refundPayload())

        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }

    /** 이미 환불된 결제에 취소가 다시 와도 아무것도 하지 않는다. */
    @Test
    @DisplayName("환불이 반복돼도 구독 해제를 다시 하지 않는다")
    fun repeatedRefundIsANoOp() {
        givenSubscriptionRefund(paymentStatus = PaymentStatus.REFUNDED)

        handle(refundPayload())

        verify(exactly = 0) { subscriptionRepository.update(any()) }
        verify(exactly = 0) { paymentRepository.update(any()) }
    }

    /**
     * 구독을 못 찾으면 **던져서 롤백**시킨다. 조용히 넘어가면 환불은 됐는데 권한만 남는다.
     */
    @Test
    @DisplayName("해제할 구독이 없으면 던져서 결제 갱신까지 되돌린다")
    fun missingSubscriptionRollsBack() {
        givenSubscriptionRefund(existing = null)

        val error = assertThrows<IllegalStateException> { handle(refundPayload()) }

        assertTrue("구독" in error.message!!, error.message!!)
        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }

    // ── 잔여 결함 회귀 ───────────────────────────────────────────────────────

    /**
     * **`substringAfter` 는 접두사 검증이 아니다.**
     *
     * 구분자가 없으면 원문을, 중간에 있으면 그 뒤를 돌려준다. 그래서 `xongo-1` 이 `1` 로
     * 읽혀 **남의 결제를 가리킬 수 있었다.**
     */
    @Test
    @DisplayName("접두사가 정확하지 않은 주문 번호는 거절한다")
    fun orderIdPrefixMustBeExact() {
        guardRunsBusiness()

        for (bad in listOf("xongo-1", " ongo-1", "prefix-ongo-1", "ONGO-1")) {
            val error = assertThrows<IllegalStateException> { handle(payload(orderId = bad)) }
            assertTrue("주문 번호" in error.message!!, "$bad → ${error.message}")
        }

        verify(exactly = 0) { paymentRepository.findByIdForUpdate(any()) }
    }

    /** 접미사가 비었거나 숫자가 아니거나 양수가 아니면 식별자가 될 수 없다. */
    @Test
    @DisplayName("접미사가 양의 정수가 아니면 거절한다")
    fun orderIdSuffixMustBePositiveNumber() {
        guardRunsBusiness()

        for (bad in listOf("ongo-", "ongo-abc", "ongo--5", "ongo-0", "ongo-1.5")) {
            assertThrows<IllegalStateException> { handle(payload(orderId = bad)) }
        }

        verify(exactly = 0) { paymentRepository.findByIdForUpdate(any()) }
    }

    /** 정상 주문 번호는 그대로 통과한다 — 과도한 차단 회귀를 막는다. */
    @Test
    @DisplayName("정상 주문 번호는 그대로 읽는다")
    fun validOrderIdIsAccepted() {
        guardRunsBusiness()
        every { paymentRepository.findByIdForUpdate(42L) } returns payment()
        every { paymentRepository.update(any()) } answers { firstArg() }

        handle(payload(orderId = "ongo-42"))

        verify(exactly = 1) { paymentRepository.findByIdForUpdate(42L) }
    }

    /**
     * **지급된 적 없는 크레딧을 회수하지 않는다.**
     *
     * `PENDING` 결제에 취소가 오면 크레딧은 애초에 지급되지 않았다. 그런데도 회수하면
     * 고객이 다른 경로로 산 잔액을 깎는다.
     */
    @Test
    @DisplayName("승인된 적 없는 크레딧 결제의 취소는 크레딧을 회수하지 않는다")
    fun pendingCreditCancellationDoesNotRevoke() {
        val pkg = CreditPackage.entries.first()
        guardRunsBusiness()
        every {
            paymentRepository.findByIdForUpdate(42L)
        } returns payment(status = PaymentStatus.PENDING, amount = pkg.price)
        every { paymentRepository.update(any()) } answers { firstArg() }

        handle(payload(status = "CANCELED", totalAmount = pkg.price))

        verify(exactly = 0) { creditService.revokeCredits(any(), any(), any()) }
    }

    /** 승인됐던 결제의 환불은 예전처럼 회수한다 — 회귀 방지. */
    @Test
    @DisplayName("승인됐던 크레딧 결제의 환불은 여전히 회수한다")
    fun completedCreditRefundStillRevokes() {
        val pkg = CreditPackage.entries.first()
        guardRunsBusiness()
        every {
            paymentRepository.findByIdForUpdate(42L)
        } returns payment(status = PaymentStatus.COMPLETED, amount = pkg.price)
        every { paymentRepository.update(any()) } answers { firstArg() }

        handle(payload(status = "CANCELED", totalAmount = pkg.price))

        verify(exactly = 1) { creditService.revokeCredits(7L, pkg.credits, "PAYMENT_REFUND:42") }
    }
}
