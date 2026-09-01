package com.ongo.application.subscription

import com.ongo.application.portone.PortOneBillingChargeException
import com.ongo.application.portone.PortOneBillingChargeRequest
import com.ongo.application.portone.PortOnePayment
import com.ongo.application.portone.PortOnePaymentGateway
import com.ongo.application.portone.PortOnePaymentResult
import com.ongo.application.portone.PortOnePaymentService
import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import com.ongo.domain.payment.Payment
import com.ongo.domain.payment.PaymentRepository
import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.notification.Notification
import com.ongo.domain.notification.NotificationRepository
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRenewalAttempt
import com.ongo.domain.subscription.SubscriptionRenewalAttemptRepository
import com.ongo.domain.subscription.SubscriptionRenewalOutcome
import com.ongo.domain.subscription.SubscriptionRepository
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import com.ongo.application.credit.CreditService
import com.ongo.common.exception.CreditNotFoundException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import io.mockk.verify
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 구독 갱신 한 건.
 *
 * 이 경로가 없을 때는 결제 주기가 지나도 아무 일도 일어나지 않아, 한 번 결제한 고객이
 * 영구히 유료 플랜을 썼다. 여기서 고정하는 것은 셋이다.
 *
 *  1. **주기당 한 번** — 원장 삽입이 청구보다 먼저이고, 자리를 얻은 호출만 움직인다
 *  2. **못 받았으면 내린다** — 청구 실패·수단 없음 모두 PAST_DUE 로 전이
 *  3. **금액을 다시 확인한다** — PG 승인액이 다르면 성공으로 치지 않는다
 *
 * 실제 PortOne 호출은 하지 않는다. gateway 계약만 mock 으로 검증한다.
 */
class SubscriptionRenewalServiceTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true)
    private val renewalAttemptRepository = mockk<SubscriptionRenewalAttemptRepository>()
    private val notificationRepository = mockk<NotificationRepository>(relaxed = true)
    private val gateway = mockk<PortOnePaymentGateway>()
    private val tokenEncryptionPort = mockk<TokenEncryptionPort>()

    private val paymentRepository = mockk<PaymentRepository>()
    private val paymentService = mockk<PortOnePaymentService>(relaxed = true)
    private val creditService = mockk<CreditService>()

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

    /*
     * 정상 계정은 가입 시 크레딧 원장이 만들어져 있다. 갱신은 청구 전에 그 존재를 확인하므로
     * 기본 픽스처를 정상 상태로 둔다 — 원장 부재 계약은 아래 전용 테스트가 고정한다.
     */
    @BeforeEach
    fun stubLedgerPresent() {
        every { creditService.ensureAccountPresence(any()) } returns Unit
    }

    /** 갱신 주기마다 만들어지는 내부 결제 원장. 외부 id 는 `ongo-{id}` 가 된다. */
    private val internalPaymentId = 4242L

    private val now = LocalDateTime.parse("2026-09-01T02:00:00")
    private val periodEnd = LocalDateTime.parse("2026-09-01T00:00:00")

    private fun subscription(
        billingKeyEncrypted: String? = "enc-key",
        price: Int = 19_900,
        cycle: BillingCycle = BillingCycle.MONTHLY,
    ) = Subscription(
        id = 5L,
        userId = 7L,
        planType = PlanType.PRO,
        status = SubscriptionStatus.ACTIVE,
        price = price,
        billingCycle = cycle,
        currentPeriodStart = periodEnd.minusMonths(1),
        currentPeriodEnd = periodEnd,
        nextBillingDate = periodEnd,
        billingKeyEncrypted = billingKeyEncrypted,
    )

    private fun paid(amount: Int = 19_900) = PortOnePayment(
        paymentId = "sub-5-renew-2026-09-01",
        status = "PAID",
        amount = amount,
        currency = "KRW",
        transactionId = "tx",
        paymentMethod = "card",
        receiptUrl = null,
    )

    private fun claimGranted() {
        every { renewalAttemptRepository.claimPeriod(any()) } returns 99L
        every { renewalAttemptRepository.completeOutcome(any(), any()) } returns Unit
        every { renewalAttemptRepository.linkPayment(any(), any()) } returns Unit
        every { paymentRepository.save(any()) } answers {
            firstArg<Payment>().copy(id = internalPaymentId)
        }
        // 실패 분기는 결제 원장을 FAILED 로 닫는다. 기본값은 아직 PENDING 인 행이다.
        every { paymentRepository.findByIdForUpdate(internalPaymentId) } returns pendingPayment()
        every { paymentRepository.update(any()) } answers { firstArg() }
    }

    /* ---- 성공 ---- */

    /**
     * **정산의 단일 권위.**
     *
     * 예전에는 이 서비스가 직접 기간을 늘렸는데(extendPeriod), 그러면 웹훅 경로와 규칙이
     * 갈라졌다 — 웹훅은 크레딧을 적용하고 갱신은 적용하지 않았으며 기준점도 달랐다.
     * 이제 기간·크레딧은 `complete()` 안의 completeSubscription 한 곳에서만 적용한다.
     */
    @Test
    fun `청구에 성공하면 정산을 결제 서비스에 위임한다`() {
        claimGranted()
        every { tokenEncryptionPort.decrypt(EncryptedToken("enc-key")) } returns PlainToken("bk_live")
        every { gateway.payWithBillingKey(any()) } returns paid()

        val outcome = service.renew(subscription(), now)

        assertEquals(SubscriptionRenewalOutcome.CHARGED, outcome)
        verify(exactly = 1) { paymentService.complete(null, "ongo-$internalPaymentId") }
        // 기간을 직접 쓰면 규칙이 다시 갈라진다.
        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }

    /* ---- 결제 원장 ---- */

    /**
     * 갱신 결제가 내부 원장에 남지 않으면 고객은 매달 빠져나간 돈을 결제 내역에서 볼 수
     * 없고, PortOne 대시보드와 대조할 기록도 없다.
     */
    @Test
    fun `선점과 같은 커밋에서 PENDING 구독 결제를 만들고 연결한다`() {
        claimGranted()
        every { tokenEncryptionPort.decrypt(any()) } returns PlainToken("bk_live")
        every { gateway.payWithBillingKey(any()) } returns paid()
        val saved = slot<Payment>()
        every { paymentRepository.save(capture(saved)) } answers { saved.captured.copy(id = internalPaymentId) }

        service.renew(subscription(), now)

        assertEquals(PaymentStatus.PENDING, saved.captured.status)
        assertEquals(PaymentType.SUBSCRIPTION, saved.captured.type)
        assertEquals(7L, saved.captured.userId)
        assertEquals(19_900, saved.captured.amount)
        assertEquals("KRW", saved.captured.currency)
        // 일반 구독 결제와 구분돼야 결제 내역에서 자동 청구임이 드러난다.
        assertEquals("SUBSCRIPTION_RENEWAL|PRO|MONTHLY", saved.captured.description)
        verify(exactly = 1) { renewalAttemptRepository.linkPayment(99L, internalPaymentId) }
    }

    /**
     * 결제를 먼저 만들면 선점 실패 시 아무도 가리키지 않는 PENDING 결제가 남고,
     * 그건 고객 결제 내역에 유령 행으로 보인다.
     */
    @Test
    fun `선점에 실패하면 결제 원장을 만들지 않는다`() {
        every { renewalAttemptRepository.claimPeriod(any()) } returns null
        every { renewalAttemptRepository.findByPeriod(any(), any()) } returns
            attempt(outcome = SubscriptionRenewalOutcome.CHARGED)

        service.renew(subscription(), now)

        verify(exactly = 0) { paymentRepository.save(any()) }
        verify(exactly = 0) { renewalAttemptRepository.linkPayment(any(), any()) }
    }

    /** 외부 결제 id 가 `ongo-` 여야 웹훅 파서가 우리 원장을 찾는다. 환불이 여기 걸린다. */
    @Test
    fun `외부 결제 id 를 ongo 접두사로 통일한다`() {
        claimGranted()
        every { tokenEncryptionPort.decrypt(any()) } returns PlainToken("bk_live")
        val request = slot<PortOneBillingChargeRequest>()
        every { gateway.payWithBillingKey(capture(request)) } returns paid()

        service.renew(subscription(), now)

        assertEquals("ongo-$internalPaymentId", request.captured.paymentId)
        assertTrue(!request.captured.paymentId.startsWith("sub-"), "레거시 형식이 남아 있다")
    }

    /**
     * 우리가 보낸 금액을 우리가 검증하면 의미가 없다. PG 승인액을 본다.
     *
     * 승인은 됐으므로 돈이 이미 움직였다. 실패로 내리면 결제한 고객의 권한을 뺏으므로
     * 구독 상태는 건드리지 않고 사람 확인 대상으로 남긴다.
     */
    @Test
    fun `첫 청구의 승인 금액이 다르면 사람 확인 대상이 된다`() {
        claimGranted()
        every { tokenEncryptionPort.decrypt(any()) } returns PlainToken("bk_live")
        every { gateway.payWithBillingKey(any()) } returns paid(amount = 100)

        val outcome = service.renew(subscription(), now)

        assertEquals(SubscriptionRenewalOutcome.NEEDS_REVIEW, outcome)
        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }

    /* ---- 실패 → PAST_DUE ---- */

    /**
     * 이 전이가 없으면 기존 유예 로직(3일 알림 → 7일 Free)이 영원히 실행되지 않는다.
     * findPastDue 가 status='PAST_DUE' 인 행만 조회하기 때문이다.
     */
    @Test
    fun `청구가 거절되면 PAST_DUE 로 내리고 재결제를 안내한다`() {
        claimGranted()
        every { tokenEncryptionPort.decrypt(any()) } returns PlainToken("bk_live")
        every { gateway.payWithBillingKey(any()) } throws PortOneBillingChargeException("거절")
        // 재조회에도 없다 = PG 에 닿지 못했다 = 돈이 움직이지 않았다.
        every { gateway.findPayment(any()) } returns null
        val saved = slot<Subscription>()
        every { subscriptionRepository.update(capture(saved)) } answers { saved.captured }
        val notified = slot<Notification>()
        every { notificationRepository.save(capture(notified)) } answers { notified.captured }

        val outcome = service.renew(subscription(), now)

        assertEquals(SubscriptionRenewalOutcome.CHARGE_FAILED, outcome)
        assertEquals(SubscriptionStatus.PAST_DUE, saved.captured.status)
        assertEquals(7L, notified.captured.userId)
        assertTrue(notified.captured.message.contains("다시 결제"))
    }

    /**
     * 현재 결제 UI 가 빌링키를 발급하지 않아 기존 구독은 전부 이 경로다. 없는 수단을
     * 있는 척하지 않고, 청구를 시도조차 하지 않는다.
     */
    @Test
    fun `빌링키가 없으면 청구를 시도하지 않고 정직하게 실패로 남긴다`() {
        claimGranted()
        val saved = slot<Subscription>()
        every { subscriptionRepository.update(capture(saved)) } answers { saved.captured }

        val outcome = service.renew(subscription(billingKeyEncrypted = null), now)

        assertEquals(SubscriptionRenewalOutcome.BILLING_KEY_MISSING, outcome)
        assertEquals(SubscriptionStatus.PAST_DUE, saved.captured.status)
        verify(exactly = 0) { gateway.payWithBillingKey(any()) }
        verify(exactly = 0) { tokenEncryptionPort.decrypt(any()) }
    }

    @Test
    fun `빌링키가 공백이면 없는 것으로 본다`() {
        claimGranted()
        every { subscriptionRepository.update(any()) } answers { firstArg() }

        assertEquals(
            SubscriptionRenewalOutcome.BILLING_KEY_MISSING,
            service.renew(subscription(billingKeyEncrypted = "   "), now),
        )
        verify(exactly = 0) { gateway.payWithBillingKey(any()) }
    }

    /** 복호화 실패는 키 교체·데이터 손상이다. 그 값으로 청구를 시도하면 안 된다. */
    @Test
    fun `빌링키 복호화에 실패하면 청구하지 않는다`() {
        claimGranted()
        every { tokenEncryptionPort.decrypt(any()) } throws IllegalStateException("bad key")
        every { subscriptionRepository.update(any()) } answers { firstArg() }

        assertEquals(
            SubscriptionRenewalOutcome.BILLING_KEY_MISSING,
            service.renew(subscription(), now),
        )
        verify(exactly = 0) { gateway.payWithBillingKey(any()) }
    }

    /* ---- 멱등 ---- */

    /**
     * 스케줄러가 하루 두 번 돌거나 인스턴스가 둘이면 같은 주기가 두 번 들어온다.
     * 돈이 두 번 빠져나가는 것이 알림 한 번 빠지는 것보다 훨씬 비싸다.
     */
    @Test
    fun `이미 처리한 주기는 청구도 상태 변경도 하지 않는다`() {
        every { renewalAttemptRepository.claimPeriod(any()) } returns null
        every { renewalAttemptRepository.findByPeriod(any(), any()) } returns
            SubscriptionRenewalAttempt(
                id = 99L,
                subscriptionId = 5L,
                periodStart = periodEnd,
                outcome = SubscriptionRenewalOutcome.CHARGED,
            )

        val outcome = service.renew(subscription(), now)

        assertNull(outcome)
        verify(exactly = 0) { gateway.payWithBillingKey(any()) }
        verify(exactly = 0) { subscriptionRepository.update(any()) }
        verify(exactly = 0) { notificationRepository.save(any()) }
    }

    /** 주기의 신원은 만료된 주기의 끝이다. now 를 쓰면 실행 시각마다 다른 주기가 된다. */
    @Test
    fun `원장에 만료된 주기의 끝을 주기 신원으로 남긴다`() {
        val attempt = slot<SubscriptionRenewalAttempt>()
        every { renewalAttemptRepository.claimPeriod(capture(attempt)) } returns 99L
        every { renewalAttemptRepository.completeOutcome(any(), any()) } returns Unit
        every { renewalAttemptRepository.linkPayment(any(), any()) } returns Unit
        every { paymentRepository.save(any()) } answers { firstArg<Payment>().copy(id = internalPaymentId) }
        every { paymentRepository.findByIdForUpdate(internalPaymentId) } returns pendingPayment()
        every { paymentRepository.update(any()) } answers { firstArg() }
        every { subscriptionRepository.update(any()) } answers { firstArg() }

        service.renew(subscription(billingKeyEncrypted = null), now)

        assertEquals(5L, attempt.captured.subscriptionId)
        assertEquals(periodEnd, attempt.captured.periodStart)
        // 선점 시점에는 결과를 모른다. 확정값을 미리 적으면 죽은 프로세스가 성공으로 남는다.
        assertEquals(SubscriptionRenewalOutcome.ATTEMPTED, attempt.captured.outcome)
    }

    /** 선점 행의 결과는 청구가 끝난 뒤 정확히 한 번 채워져야 한다. */
    @Test
    fun `청구가 끝나면 선점 행의 결과를 채운다`() {
        claimGranted()
        every { tokenEncryptionPort.decrypt(any()) } returns PlainToken("bk_live")
        every { gateway.payWithBillingKey(any()) } returns paid()
        every { subscriptionRepository.update(any()) } answers { firstArg() }

        service.renew(subscription(), now)

        verify(exactly = 1) {
            renewalAttemptRepository.completeOutcome(99L, SubscriptionRenewalOutcome.CHARGED)
        }
    }

    /**
     * 청구 후에 기록하면 청구는 성공했는데 기록이 실패하는 창이 생기고, 다음 실행이 같은
     * 주기를 다시 청구한다. 자리를 먼저 잡는 순서를 고정한다.
     */
    /* ---- 복구: 결과를 못 채운 주기 ---- */

    /**
     * **이 시나리오가 이중 청구의 실체다.**
     *
     * PG 청구는 성공했는데 정산 커밋(구독 기간 연장)이 예외로 실패하면 선점 행이
     * ATTEMPTED 로 남는다. 다음 due 실행이 이걸 보고 **다시 청구하면 돈이 두 번 나간다.**
     * 재청구가 아니라 findPayment 재조회로 결말을 지어야 한다.
     */
    @Test
    fun `정산 실패로 남은 주기는 재청구하지 않고 재조회로 정산한다`() {
        // 1회차: 청구는 성공했지만 정산이 터진다.
        claimGranted()
        every { tokenEncryptionPort.decrypt(any()) } returns PlainToken("bk_live")
        every { gateway.payWithBillingKey(any()) } returns paid()
        every { paymentService.complete(any(), any()) } throws IllegalStateException("db down")

        runCatching { service.renew(subscription(), now) }

        // 2회차: 선점은 이미 있고 결과는 ATTEMPTED 로 남아 있다.
        // 보호 유예를 지난 행이어야 재조회 대상이 된다.
        every { renewalAttemptRepository.claimPeriod(any()) } returns null
        every { renewalAttemptRepository.findByPeriod(5L, periodEnd) } returns
            attempt(createdAt = now.minusMinutes(30), paymentId = internalPaymentId)
        every { gateway.findPayment("ongo-$internalPaymentId") } returns paid()
        every { paymentService.complete(any(), any()) } returns
            PortOnePaymentResult(id = internalPaymentId, status = "COMPLETED")

        val outcome = service.renew(subscription(), now.plusDays(1))

        assertEquals(SubscriptionRenewalOutcome.CHARGED, outcome)
        /*
         * 정산 위임은 두 번 일어난다 — 1회차(예외로 끝남)와 2회차(성공). 둘 다 같은
         * paymentId 를 쓰므로 complete() 안의 조기 반환이 실제 적용을 한 번으로 만든다.
         * 여기서 검증하는 것은 **같은 결제 id 로만 위임한다**는 것이다.
         */
        verify(exactly = 2) { paymentService.complete(null, "ongo-$internalPaymentId") }
        // 재청구는 1회차의 한 번뿐이어야 한다.
        verify(exactly = 1) { gateway.payWithBillingKey(any()) }
        verify(exactly = 1) { gateway.findPayment("ongo-$internalPaymentId") }
    }

    /* ---- 실패한 갱신의 결제 원장 마감 ---- */

    private fun pendingPayment() = Payment(
        id = internalPaymentId,
        userId = 7L,
        type = PaymentType.SUBSCRIPTION,
        amount = 19_900,
        currency = "KRW",
        status = PaymentStatus.PENDING,
        description = "SUBSCRIPTION_RENEWAL|PRO|MONTHLY",
    )

    /**
     * 닫지 않으면 PENDING 이 영구히 남아 고객의 결제 내역에 **영영 끝나지 않는 결제**가
     * 보인다. 다음 달 갱신이 새 PENDING 을 또 만들면 그 목록이 계속 늘어난다.
     */
    @Test
    fun `청구가 거절되면 결제 원장을 FAILED 로 닫는다`() {
        claimGranted()
        every { tokenEncryptionPort.decrypt(any()) } returns PlainToken("bk_live")
        every { gateway.payWithBillingKey(any()) } throws PortOneBillingChargeException("거절")
        every { gateway.findPayment(any()) } returns null
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { paymentRepository.findByIdForUpdate(internalPaymentId) } returns pendingPayment()
        val closed = slot<Payment>()
        every { paymentRepository.update(capture(closed)) } answers { closed.captured }

        assertEquals(SubscriptionRenewalOutcome.CHARGE_FAILED, service.renew(subscription(), now))

        assertEquals(PaymentStatus.FAILED, closed.captured.status)
        assertEquals(internalPaymentId, closed.captured.id)
    }

    @Test
    fun `빌링키가 없어도 결제 원장을 FAILED 로 닫는다`() {
        claimGranted()
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { paymentRepository.findByIdForUpdate(internalPaymentId) } returns pendingPayment()
        val closed = slot<Payment>()
        every { paymentRepository.update(capture(closed)) } answers { closed.captured }

        assertEquals(
            SubscriptionRenewalOutcome.BILLING_KEY_MISSING,
            service.renew(subscription(billingKeyEncrypted = null), now),
        )

        assertEquals(PaymentStatus.FAILED, closed.captured.status)
    }

    /**
     * 그 사이 웹훅이 도착해 COMPLETED 가 됐을 수 있다. FAILED 로 덮으면 실제로 빠져나간
     * 돈을 실패로 기록한다.
     */
    @Test
    fun `이미 확정된 결제는 실패로 덮지 않는다`() {
        claimGranted()
        every { tokenEncryptionPort.decrypt(any()) } returns PlainToken("bk_live")
        every { gateway.payWithBillingKey(any()) } throws PortOneBillingChargeException("거절")
        every { gateway.findPayment(any()) } returns null
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { paymentRepository.findByIdForUpdate(internalPaymentId) } returns
            pendingPayment().copy(status = PaymentStatus.COMPLETED)

        service.renew(subscription(), now)

        verify(exactly = 0) { paymentRepository.update(any()) }
        // 완료 웹훅의 ACTIVE/연장 기간을 오래된 실패 스냅샷으로 덮지 않는다.
        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }

    /**
     * 청구 판정과 정산 사이에 웹훅이 같은 결제를 완료했다.
     *
     * 결제·구독을 건드리지 않는 것만으로는 부족하다. **갱신 원장까지 확정하면 안 된다.**
     * attempt 만 CHARGE_FAILED 가 되면 결제는 성공인데 갱신 원장은 미결제가 되어 두 원장이
     * 갈리고, 그 뒤로는 어느 쪽이 사실인지 코드로 판별할 수 없다.
     *
     * ATTEMPTED 로 남겨야 다음 실행이 유예를 지나 재조회로 사실을 확정한다. 그래서 이번
     * 실행의 결과는 "확정하지 못함"(null)이다.
     */
    @Test
    fun `웹훅이 먼저 완료한 결제는 갱신 원장도 확정하지 않는다`() {
        listOf(PaymentStatus.COMPLETED, PaymentStatus.REFUNDED).forEach { settled ->
            claimGranted()
            every { tokenEncryptionPort.decrypt(any()) } returns PlainToken("bk_live")
            every { gateway.payWithBillingKey(any()) } throws PortOneBillingChargeException("거절")
            every { gateway.findPayment(any()) } returns null
            every { subscriptionRepository.update(any()) } answers { firstArg() }
            every { paymentRepository.findByIdForUpdate(internalPaymentId) } returns
                pendingPayment().copy(status = settled)

            assertNull(service.renew(subscription(), now), "status=$settled")

            // 갱신 원장 확정을 시도조차 하지 않는다.
            verify(exactly = 0) { renewalAttemptRepository.completeOutcome(any(), any()) }
            verify(exactly = 0) { paymentRepository.update(any()) }
            verify(exactly = 0) { subscriptionRepository.update(any()) }
            clearMocks(renewalAttemptRepository, paymentRepository, subscriptionRepository, gateway)
        }
    }

    /**
     * 빌링키가 없으면 **외부 호출 자체가 없었다.** 웹훅이 이 주기의 결제를 완료했을
     * 가능성이 없고, 결과도 PG 가 아니라 우리 설정에 대한 사실이다. 그래서 결제 원장이
     * 어떻든 확정한다 — 청구 실패와 구분해야 운영자가 할 일이 달라진다.
     */
    @Test
    fun `결제수단 미등록은 결제 원장 상태와 무관하게 확정한다`() {
        claimGranted()
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { paymentRepository.findByIdForUpdate(internalPaymentId) } returns
            pendingPayment().copy(status = PaymentStatus.COMPLETED)

        assertEquals(
            SubscriptionRenewalOutcome.BILLING_KEY_MISSING,
            service.renew(subscription(billingKeyEncrypted = null), now),
        )

        verify(exactly = 1) {
            renewalAttemptRepository.completeOutcome(99L, SubscriptionRenewalOutcome.BILLING_KEY_MISSING)
        }
        // 다만 이미 결말난 결제를 덮거나 구독을 내리지는 않는다.
        verify(exactly = 0) { paymentRepository.update(any()) }
        verify(exactly = 0) { subscriptionRepository.update(any()) }
        verify(exactly = 0) { gateway.payWithBillingKey(any()) }
    }

    /**
     * 승인 여부·금액·통화가 불명이다. 실패로 적으면 실제로 빠져나간 돈을 "실패"로
     * 기록하게 된다. 미확정은 미확정으로 남는 편이 정직하다.
     */
    @Test
    fun `NEEDS_REVIEW 는 결제 원장을 PENDING 으로 남긴다`() {
        claimGranted()
        every { tokenEncryptionPort.decrypt(any()) } returns PlainToken("bk_live")
        every { gateway.payWithBillingKey(any()) } returns paid(amount = 100)

        assertEquals(SubscriptionRenewalOutcome.NEEDS_REVIEW, service.renew(subscription(), now))

        verify(exactly = 0) { paymentRepository.findByIdForUpdate(any()) }
        verify(exactly = 0) { paymentRepository.update(any()) }
    }

    /** 레거시 주기는 내부 원장이 없어 닫을 대상도 없다. */
    @Test
    fun `내부 원장 없는 주기는 결제 원장을 건드리지 않는다`() {
        every { renewalAttemptRepository.claimPeriod(any()) } returns null
        every { renewalAttemptRepository.completeOutcome(any(), any()) } returns Unit
        every { renewalAttemptRepository.findByPeriod(5L, periodEnd) } returns
            attempt(createdAt = now.minusMinutes(30), paymentId = null)
        every { gateway.findPayment(any()) } returns null
        every { subscriptionRepository.update(any()) } answers { firstArg() }

        assertEquals(SubscriptionRenewalOutcome.CHARGE_FAILED, service.renew(subscription(), now))

        verify(exactly = 0) { paymentRepository.findByIdForUpdate(any()) }
        verify(exactly = 0) { paymentRepository.update(any()) }
    }

    /* ---- 웹훅/settle 경합 ---- */

    /**
     * 웹훅이 먼저 도착해 결제를 COMPLETED 로 만든 뒤 settle 이 돈다.
     *
     * 예전에는 settle 이 extendPeriod 로 **직접** 기간을 늘려, 웹훅이 이미 늘린 기간 위에
     * 또 한 달을 얹거나 낡은 스냅샷 값으로 되돌렸다. 이제 complete() 에 위임하므로
     * 조기 반환이 그것을 막는다.
     */
    @Test
    fun `웹훅이 먼저 처리해도 정산은 한 번만 일어난다`() {
        claimGranted()
        every { tokenEncryptionPort.decrypt(any()) } returns PlainToken("bk_live")
        every { gateway.payWithBillingKey(any()) } returns paid()
        // 웹훅이 이미 완료시킨 결제. complete() 는 조기 반환한다.
        every { paymentService.complete(null, "ongo-$internalPaymentId") } returns
            PortOnePaymentResult(id = internalPaymentId, status = "COMPLETED")

        val outcome = service.renew(subscription(), now)

        assertEquals(SubscriptionRenewalOutcome.CHARGED, outcome)
        verify(exactly = 1) { paymentService.complete(null, "ongo-$internalPaymentId") }
        // 기간을 직접 건드리지 않으므로 이중 연장이 구조적으로 불가능하다.
        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }

    /**
     * settle 이 먼저 끝난 뒤 늦은 웹훅이 도착해도 attempt 는 이미 최종화돼 있어야 하고,
     * 스케줄러가 같은 주기를 다시 잡지 않아야 한다.
     */
    @Test
    fun `settle 이 먼저 끝나면 attempt 가 확정되어 다음 실행이 건드리지 않는다`() {
        claimGranted()
        every { tokenEncryptionPort.decrypt(any()) } returns PlainToken("bk_live")
        every { gateway.payWithBillingKey(any()) } returns paid()

        service.renew(subscription(), now)
        verify(exactly = 1) {
            renewalAttemptRepository.completeOutcome(99L, SubscriptionRenewalOutcome.CHARGED)
        }

        // 다음 실행: 확정된 주기는 재조회조차 하지 않는다.
        every { renewalAttemptRepository.claimPeriod(any()) } returns null
        every { renewalAttemptRepository.findByPeriod(any(), any()) } returns
            attempt(outcome = SubscriptionRenewalOutcome.CHARGED, paymentId = internalPaymentId)

        assertNull(service.renew(subscription(), now.plusDays(1)))
        verify(exactly = 1) { gateway.payWithBillingKey(any()) }
        verify(exactly = 0) { gateway.findPayment(any()) }
    }

    /* ---- 레거시 주기 (V103 이전) ---- */

    /**
     * payment_id 가 null 인 주기는 내부 원장이 없다. 레거시 형식으로 조회하되, PG 에
     * 결제가 있으면 정산할 대상이 없으므로 자동으로 정하지 않고 사람이 확인하게 남긴다.
     */
    @Test
    fun `내부 원장 없는 주기는 레거시 id 로 조회하고 NEEDS_REVIEW 로 남긴다`() {
        every { renewalAttemptRepository.claimPeriod(any()) } returns null
        every { renewalAttemptRepository.completeOutcome(any(), any()) } returns Unit
        every { renewalAttemptRepository.findByPeriod(5L, periodEnd) } returns
            attempt(createdAt = now.minusMinutes(30), paymentId = null)
        every { gateway.findPayment("sub-5-renew-2026-09-01") } returns paid()

        val outcome = service.renew(subscription(), now)

        assertEquals(SubscriptionRenewalOutcome.NEEDS_REVIEW, outcome)
        verify(exactly = 1) { gateway.findPayment("sub-5-renew-2026-09-01") }
        // 재청구도, 정산도 하지 않는다.
        verify(exactly = 0) { gateway.payWithBillingKey(any()) }
        verify(exactly = 0) { paymentService.complete(any(), any()) }
        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }

    /** 레거시 주기라도 PG 에 결제가 없으면 돈이 움직이지 않은 것이 확실하다. */
    @Test
    fun `내부 원장 없는 주기의 결제가 PG 에도 없으면 미결제로 확정한다`() {
        every { renewalAttemptRepository.claimPeriod(any()) } returns null
        every { renewalAttemptRepository.completeOutcome(any(), any()) } returns Unit
        every { renewalAttemptRepository.findByPeriod(5L, periodEnd) } returns
            attempt(createdAt = now.minusMinutes(30), paymentId = null)
        every { gateway.findPayment(any()) } returns null
        val saved = slot<Subscription>()
        every { subscriptionRepository.update(capture(saved)) } answers { saved.captured }

        assertEquals(SubscriptionRenewalOutcome.CHARGE_FAILED, service.renew(subscription(), now))
        assertEquals(SubscriptionStatus.PAST_DUE, saved.captured.status)
    }

    /** PG 가 그 id 를 모른다 = 돈이 움직이지 않았다. 이때만 미결제로 단정할 수 있다. */
    @Test
    fun `남은 주기의 결제가 PG 에 없으면 미결제로 확정하고 재청구하지 않는다`() {
        staleClaim()
        every { gateway.findPayment(any()) } returns null
        val saved = slot<Subscription>()
        every { subscriptionRepository.update(capture(saved)) } answers { saved.captured }

        val outcome = service.renew(subscription(), now)

        assertEquals(SubscriptionRenewalOutcome.CHARGE_FAILED, outcome)
        assertEquals(SubscriptionStatus.PAST_DUE, saved.captured.status)
        verify(exactly = 0) { gateway.payWithBillingKey(any()) }
    }

    /**
     * 조회가 실패하면 결제됐는지 아닌지 **모른다.** 모르는 것을 실패로 적으면 이미
     * 빠져나간 돈을 못 본 채 고객을 PAST_DUE 로 내리게 된다.
     */
    @Test
    fun `재조회가 실패하면 결과를 확정하지 않고 ATTEMPTED 로 남긴다`() {
        staleClaim()
        every { gateway.findPayment(any()) } throws RuntimeException("timeout")

        val outcome = service.renew(subscription(), now)

        assertNull(outcome)
        verify(exactly = 0) { renewalAttemptRepository.completeOutcome(any(), any()) }
        verify(exactly = 0) { subscriptionRepository.update(any()) }
        verify(exactly = 0) { gateway.payWithBillingKey(any()) }
    }

    /**
     * 돈은 움직였는데 금액이 다르다. 실패로 내리면 결제한 고객의 권한을 뺏고, 성공으로
     * 잡으면 틀린 금액이 매출이 된다. 사람이 봐야 하고, 구독 상태는 건드리지 않는다.
     */
    @Test
    fun `승인 금액이 다르면 사람 확인 대상으로 남기고 구독 상태를 바꾸지 않는다`() {
        staleClaim()
        every { gateway.findPayment(any()) } returns paid(amount = 9_900)

        val outcome = service.renew(subscription(), now)

        assertEquals(SubscriptionRenewalOutcome.NEEDS_REVIEW, outcome)
        verify(exactly = 1) {
            renewalAttemptRepository.completeOutcome(99L, SubscriptionRenewalOutcome.NEEDS_REVIEW)
        }
        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }

    /** 이미 결말이 난 주기는 조회도 청구도 하지 않는다. */
    @Test
    fun `이미 확정된 주기는 재조회조차 하지 않는다`() {
        every { renewalAttemptRepository.claimPeriod(any()) } returns null
        every { renewalAttemptRepository.findByPeriod(any(), any()) } returns
            SubscriptionRenewalAttempt(
                id = 99L,
                subscriptionId = 5L,
                periodStart = periodEnd,
                outcome = SubscriptionRenewalOutcome.CHARGED,
            )

        assertNull(service.renew(subscription(), now))
        verify(exactly = 0) { gateway.findPayment(any()) }
        verify(exactly = 0) { gateway.payWithBillingKey(any()) }
    }

    /**
     * 청구 호출 자체가 터진 경우도 실패로 단정하면 안 된다. PG 에 닿지 못한 것인지,
     * 승인 뒤 응답만 못 받은 것인지 구분할 수 없다.
     */
    @Test
    fun `청구 호출이 실패해도 재조회로 확인한 뒤 결론을 낸다`() {
        claimGranted()
        every { tokenEncryptionPort.decrypt(any()) } returns PlainToken("bk_live")
        every { gateway.payWithBillingKey(any()) } throws PortOneBillingChargeException("timeout")
        every { gateway.findPayment(any()) } returns paid()

        val outcome = service.renew(subscription(), now)

        // 응답만 못 받았을 뿐 승인은 됐다. 실패로 내렸다면 결제한 고객을 PAST_DUE 로 만들었다.
        assertEquals(SubscriptionRenewalOutcome.CHARGED, outcome)
        verify(exactly = 1) { gateway.payWithBillingKey(any()) }
        // 재조회도 같은 내부 결제 id 로 한다.
        verify(exactly = 1) { gateway.findPayment("ongo-$internalPaymentId") }
        verify(exactly = 1) { paymentService.complete(null, "ongo-$internalPaymentId") }
    }

    /** 보호 유예(10분)를 지난 ATTEMPTED. 첫 호출이 끝나지 않고 사라진 주기다. */
    private fun staleClaim() {
        every { renewalAttemptRepository.claimPeriod(any()) } returns null
        every { renewalAttemptRepository.completeOutcome(any(), any()) } returns Unit
        every { renewalAttemptRepository.findByPeriod(5L, periodEnd) } returns
            attempt(createdAt = now.minusMinutes(30))
        every { paymentRepository.findByIdForUpdate(internalPaymentId) } returns pendingPayment()
        every { paymentRepository.update(any()) } answers { firstArg() }
    }

    private fun attempt(
        outcome: SubscriptionRenewalOutcome = SubscriptionRenewalOutcome.ATTEMPTED,
        createdAt: LocalDateTime = now.minusMinutes(30),
        paymentId: Long? = internalPaymentId,
    ) = SubscriptionRenewalAttempt(
        id = 99L,
        subscriptionId = 5L,
        periodStart = periodEnd,
        outcome = outcome,
        paymentId = paymentId,
        createdAt = createdAt,
    )

    /* ---- 보호 유예: 진행 중인 선점을 건드리지 않는다 ---- */

    /**
     * 선점은 커밋됐지만 청구는 그 뒤 트랜잭션 밖에서 일어난다. 그 사이에 다른 실행이
     * 재조회하면 **아직 만들어지지 않은 결제**를 404 로 보고 미결제로 확정해 버린다 —
     * 곧이어 승인될 결제를 실패로 적고 고객을 PAST_DUE 로 내리는 것이다.
     */
    @Test
    fun `선점 직후의 ATTEMPTED 는 재조회하지 않고 그대로 둔다`() {
        every { renewalAttemptRepository.claimPeriod(any()) } returns null
        every { renewalAttemptRepository.findByPeriod(5L, periodEnd) } returns
            attempt(createdAt = now.minusMinutes(1))

        val outcome = service.renew(subscription(), now)

        assertNull(outcome)
        verify(exactly = 0) { gateway.findPayment(any()) }
        verify(exactly = 0) { gateway.payWithBillingKey(any()) }
        verify(exactly = 0) { renewalAttemptRepository.completeOutcome(any(), any()) }
        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }

    /** 유예 경계 바로 안쪽도 보호 대상이다. */
    @Test
    fun `유예 경계 안의 ATTEMPTED 도 건드리지 않는다`() {
        every { renewalAttemptRepository.claimPeriod(any()) } returns null
        every { renewalAttemptRepository.findByPeriod(5L, periodEnd) } returns
            attempt(createdAt = now.minusMinutes(9))

        assertNull(service.renew(subscription(), now))
        verify(exactly = 0) { gateway.findPayment(any()) }
    }

    /**
     * 유예가 지나면 손대야 한다. 영원히 기다리면 결과를 못 채운 주기가 방치되고
     * 구독은 ACTIVE 인 채로 무기한 무상 제공이 된다.
     */
    @Test
    fun `유예를 지난 ATTEMPTED 는 재조회로 결말을 짓는다`() {
        every { renewalAttemptRepository.claimPeriod(any()) } returns null
        every { renewalAttemptRepository.completeOutcome(any(), any()) } returns Unit
        every { renewalAttemptRepository.findByPeriod(5L, periodEnd) } returns
            attempt(createdAt = now.minusMinutes(11))
        every { gateway.findPayment(any()) } returns paid()
        every { subscriptionRepository.update(any()) } answers { firstArg() }

        assertEquals(SubscriptionRenewalOutcome.CHARGED, service.renew(subscription(), now))
        verify(exactly = 1) { gateway.findPayment(any()) }
        // 유예를 지났어도 재청구는 하지 않는다.
        verify(exactly = 0) { gateway.payWithBillingKey(any()) }
    }

    /* ---- 통화 검증 ---- */

    /**
     * 금액만 보면 19,900 KRW 청구가 19,900 USD 승인으로 돌아와도 성공으로 잡힌다.
     * 숫자는 같고 실제로 빠져나간 돈은 천 배 넘게 다르다.
     */
    @Test
    fun `승인 통화가 다르면 사람 확인 대상이 되고 구독 상태를 바꾸지 않는다`() {
        claimGranted()
        every { tokenEncryptionPort.decrypt(any()) } returns PlainToken("bk_live")
        every { gateway.payWithBillingKey(any()) } returns paid().copy(currency = "USD")

        val outcome = service.renew(subscription(), now)

        assertEquals(SubscriptionRenewalOutcome.NEEDS_REVIEW, outcome)
        verify(exactly = 1) {
            renewalAttemptRepository.completeOutcome(99L, SubscriptionRenewalOutcome.NEEDS_REVIEW)
        }
        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }

    @Test
    fun `재조회로 확인한 결제의 통화가 달라도 사람 확인 대상이다`() {
        staleClaim()
        every { gateway.findPayment(any()) } returns paid().copy(currency = "JPY")

        assertEquals(SubscriptionRenewalOutcome.NEEDS_REVIEW, service.renew(subscription(), now))
        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }

    /** 통화 표기 대소문자는 PG 마다 다를 수 있다. 그것 때문에 멀쩡한 결제를 막지 않는다. */
    @Test
    fun `통화 비교는 대소문자를 가리지 않는다`() {
        claimGranted()
        every { tokenEncryptionPort.decrypt(any()) } returns PlainToken("bk_live")
        every { gateway.payWithBillingKey(any()) } returns paid().copy(currency = "krw")
        every { subscriptionRepository.update(any()) } answers { firstArg() }

        assertEquals(SubscriptionRenewalOutcome.CHARGED, service.renew(subscription(), now))
    }

    /** 청구 요청과 검증 기준이 갈라지면 모든 갱신이 NEEDS_REVIEW 로 떨어진다. */
    @Test
    fun `청구 요청 통화와 검증 기준이 같다`() {
        claimGranted()
        every { tokenEncryptionPort.decrypt(any()) } returns PlainToken("bk_live")
        val request = slot<PortOneBillingChargeRequest>()
        every { gateway.payWithBillingKey(capture(request)) } returns paid()
        every { subscriptionRepository.update(any()) } answers { firstArg() }

        service.renew(subscription(), now)

        assertEquals("KRW", request.captured.currency)
    }

    @Test
    fun `주기 선점이 청구보다 먼저다`() {
        val order = mutableListOf<String>()
        every { renewalAttemptRepository.claimPeriod(any()) } answers { order += "claim"; 99L }
        every { renewalAttemptRepository.completeOutcome(any(), any()) } answers { order += "complete" }
        every { renewalAttemptRepository.linkPayment(any(), any()) } returns Unit
        every { paymentRepository.save(any()) } answers { firstArg<Payment>().copy(id = internalPaymentId) }
        every { tokenEncryptionPort.decrypt(any()) } returns PlainToken("bk_live")
        every { gateway.payWithBillingKey(any()) } answers { order += "charge"; paid() }
        every { subscriptionRepository.update(any()) } answers { firstArg() }

        service.renew(subscription(), now)

        assertEquals(listOf("claim", "charge", "complete"), order)
    }

    /* ---- 요청 내용 ---- */

    /** 결제 식별자가 주기마다 고정돼야 재시도가 두 번째 청구가 되지 않는다. */
    @Test
    fun `같은 주기는 항상 같은 결제 식별자를 쓴다`() {
        claimGranted()
        every { tokenEncryptionPort.decrypt(any()) } returns PlainToken("bk_live")
        val request = slot<PortOneBillingChargeRequest>()
        every { gateway.payWithBillingKey(capture(request)) } returns paid()
        every { subscriptionRepository.update(any()) } answers { firstArg() }

        service.renew(subscription(), now)
        val first = request.captured.paymentId
        service.renew(subscription(), now.plusHours(6))

        assertEquals(first, request.captured.paymentId)
    }

    /** 이름·이메일·전화번호를 PG 로 보내지 않는다. 보내는 순간 PG 로그가 개인정보 사본이 된다. */
    @Test
    fun `청구 요청에 내부 식별자 외의 고객 정보를 싣지 않는다`() {
        claimGranted()
        every { tokenEncryptionPort.decrypt(any()) } returns PlainToken("bk_live")
        val request = slot<PortOneBillingChargeRequest>()
        every { gateway.payWithBillingKey(capture(request)) } returns paid()
        every { subscriptionRepository.update(any()) } answers { firstArg() }

        service.renew(subscription(), now)

        assertEquals("7", request.captured.customerId)
        assertEquals(19_900, request.captured.amount)
        assertEquals("KRW", request.captured.currency)
        assertEquals("store-test", request.captured.storeId)
        assertEquals("channel-test", request.captured.channelKey)
        val fields = PortOneBillingChargeRequest::class.members.map { it.name }.toSet()
        for (forbidden in listOf("email", "name", "phone", "phoneNumber")) {
            assertTrue(forbidden !in fields, "청구 요청에 개인정보 필드가 생겼다: $forbidden")
        }
    }

    /* ---- 크레딧 원장 부재: 청구 전에 막는다 ---- */

    /**
     * 정산은 `paymentService.complete` → `completeSubscription` → `applyPlanEntitlement` 로
     * 끝난다. 원장이 없으면 거기서 예외가 나 정산이 통째로 롤백되는데, **그때는 PortOne
     * 청구가 이미 끝나 있다.** 돈은 빠지고 기간·크레딧은 반영되지 않으며, 다음 실행은 같은
     * 주기를 Stale 로 보고 재조회만 하므로 스스로 낫지 않는다.
     *
     * 그래서 선점보다도 먼저 판정한다. 주기가 선점되지 않은 채 남아야 원장을 복구한 뒤
     * 다음 실행이 정상적으로 청구할 수 있다 — 선점 후 실패와 달리 되돌릴 것이 없다.
     */
    @Test
    @DisplayName("크레딧 원장이 없으면 선점·결제원장·청구 어느 것도 하지 않는다")
    fun `원장 부재 - 청구 전 차단`() {
        every { creditService.ensureAccountPresence(7L) } throws CreditNotFoundException(7L)
        claimGranted()
        every { gateway.payWithBillingKey(any()) } returns paid()

        val error = assertThrows(CreditNotFoundException::class.java) {
            service.renew(subscription(), now)
        }

        assertEquals("CREDIT_NOT_FOUND", error.code)
        verify(exactly = 0) { renewalAttemptRepository.claimPeriod(any()) }
        verify(exactly = 0) { paymentRepository.save(any()) }
        verify(exactly = 0) { gateway.payWithBillingKey(any()) }
    }

    /**
     * 확인은 **선점보다 먼저**여야 한다.
     *
     * 순서가 뒤집히면 주기가 ATTEMPTED 로 선점된 채 예외가 나간다. 그 주기는 다음 실행에서
     * Stale 로 분류돼 **재청구되지 않고 재조회만** 되므로, 원장을 고쳐도 그 달 청구는
     * 영영 일어나지 않는다.
     */
    @Test
    @DisplayName("원장 확인이 주기 선점보다 먼저 끝난다")
    fun `원장 부재 - 선점보다 먼저 판정한다`() {
        val order = mutableListOf<String>()
        every { creditService.ensureAccountPresence(7L) } answers {
            order += "ledger"
            throw CreditNotFoundException(7L)
        }
        every { renewalAttemptRepository.claimPeriod(any()) } answers { order += "claim"; 99L }

        assertThrows(CreditNotFoundException::class.java) { service.renew(subscription(), now) }

        assertEquals(listOf("ledger"), order, "선점이 원장 확인보다 먼저 실행됐다: $order")
    }

    /** 정상 계정은 종전대로 청구된다 — 가드가 과하지 않은지 본다. */
    @Test
    @DisplayName("원장이 있으면 종전대로 선점하고 청구한다")
    fun `원장 존재 - 갱신은 종전대로 진행된다`() {
        claimGranted()
        every { gateway.payWithBillingKey(any()) } returns paid()
        every { tokenEncryptionPort.decrypt(any()) } returns PlainToken("billing-key")

        service.renew(subscription(), now)

        verify(exactly = 1) { creditService.ensureAccountPresence(7L) }
        verify(exactly = 1) { renewalAttemptRepository.claimPeriod(any()) }
        verify(exactly = 1) { gateway.payWithBillingKey(any()) }
    }
}
