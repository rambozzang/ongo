package com.ongo.application.subscription

import com.ongo.application.portone.PortOneBillingChargeException
import com.ongo.application.portone.PortOneBillingChargeRequest
import com.ongo.application.portone.PortOnePayment
import com.ongo.application.portone.PortOnePaymentGateway
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
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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

    private val service = SubscriptionRenewalService(
        subscriptionRepository = subscriptionRepository,
        renewalAttemptRepository = renewalAttemptRepository,
        notificationRepository = notificationRepository,
        gateway = gateway,
        tokenEncryptionPort = tokenEncryptionPort,
        transactionManager = DummyTransactionManagerForTest(),
    )

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
    }

    /* ---- 성공 ---- */

    @Test
    fun `청구에 성공하면 기간을 연장하고 다음 청구일을 갱신한다`() {
        claimGranted()
        every { tokenEncryptionPort.decrypt(EncryptedToken("enc-key")) } returns PlainToken("bk_live")
        every { gateway.payWithBillingKey(any()) } returns paid()
        val saved = slot<Subscription>()
        every { subscriptionRepository.update(capture(saved)) } answers { saved.captured }

        val outcome = service.renew(subscription(), now)

        assertEquals(SubscriptionRenewalOutcome.CHARGED, outcome)
        assertEquals(SubscriptionStatus.ACTIVE, saved.captured.status)
        // 새 주기는 만료된 주기의 끝에서 시작한다. now 기준으로 잡으면 주기가 밀린다.
        assertEquals(periodEnd, saved.captured.currentPeriodStart)
        assertEquals(periodEnd.plusMonths(1), saved.captured.currentPeriodEnd)
        assertEquals(periodEnd.plusMonths(1), saved.captured.nextBillingDate)
    }

    @Test
    fun `연간 구독은 1년을 연장한다`() {
        claimGranted()
        every { tokenEncryptionPort.decrypt(any()) } returns PlainToken("bk_live")
        every { gateway.payWithBillingKey(any()) } returns paid(price(BillingCycle.YEARLY))
        val saved = slot<Subscription>()
        every { subscriptionRepository.update(capture(saved)) } answers { saved.captured }

        service.renew(subscription(price = price(BillingCycle.YEARLY), cycle = BillingCycle.YEARLY), now)

        assertEquals(periodEnd.plusYears(1), saved.captured.currentPeriodEnd)
    }

    private fun price(cycle: BillingCycle) =
        if (cycle == BillingCycle.YEARLY) PlanType.PRO.yearlyPrice else PlanType.PRO.price

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
    fun `정산 실패로 남은 주기는 재청구하지 않고 재조회로 기간을 연장한다`() {
        // 1회차: 청구는 성공했지만 구독 갱신이 터진다.
        claimGranted()
        every { tokenEncryptionPort.decrypt(any()) } returns PlainToken("bk_live")
        every { gateway.payWithBillingKey(any()) } returns paid()
        every { subscriptionRepository.update(any()) } throws IllegalStateException("db down")

        runCatching { service.renew(subscription(), now) }

        // 2회차: 선점은 이미 있고 결과는 ATTEMPTED 로 남아 있다.
        // 보호 유예를 지난 행이어야 재조회 대상이 된다.
        every { renewalAttemptRepository.claimPeriod(any()) } returns null
        every { renewalAttemptRepository.findByPeriod(5L, periodEnd) } returns
            attempt(createdAt = now.minusMinutes(30))
        every { gateway.findPayment("sub-5-renew-2026-09-01") } returns paid()
        val saved = slot<Subscription>()
        every { subscriptionRepository.update(capture(saved)) } answers { saved.captured }

        val outcome = service.renew(subscription(), now.plusDays(1))

        assertEquals(SubscriptionRenewalOutcome.CHARGED, outcome)
        // 기간이 연장돼야 결제한 고객이 권한을 잃지 않는다.
        assertEquals(periodEnd.plusMonths(1), saved.captured.currentPeriodEnd)
        assertEquals(SubscriptionStatus.ACTIVE, saved.captured.status)
        // 재청구는 1회차의 한 번뿐이어야 한다.
        verify(exactly = 1) { gateway.payWithBillingKey(any()) }
        verify(exactly = 1) { gateway.findPayment("sub-5-renew-2026-09-01") }
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
        val saved = slot<Subscription>()
        every { subscriptionRepository.update(capture(saved)) } answers { saved.captured }

        val outcome = service.renew(subscription(), now)

        // 응답만 못 받았을 뿐 승인은 됐다. 실패로 내렸다면 결제한 고객을 PAST_DUE 로 만들었다.
        assertEquals(SubscriptionRenewalOutcome.CHARGED, outcome)
        assertEquals(SubscriptionStatus.ACTIVE, saved.captured.status)
        verify(exactly = 1) { gateway.payWithBillingKey(any()) }
    }

    /** 보호 유예(10분)를 지난 ATTEMPTED. 첫 호출이 끝나지 않고 사라진 주기다. */
    private fun staleClaim() {
        every { renewalAttemptRepository.claimPeriod(any()) } returns null
        every { renewalAttemptRepository.completeOutcome(any(), any()) } returns Unit
        every { renewalAttemptRepository.findByPeriod(5L, periodEnd) } returns
            attempt(createdAt = now.minusMinutes(30))
    }

    private fun attempt(
        outcome: SubscriptionRenewalOutcome = SubscriptionRenewalOutcome.ATTEMPTED,
        createdAt: LocalDateTime = now.minusMinutes(30),
    ) = SubscriptionRenewalAttempt(
        id = 99L,
        subscriptionId = 5L,
        periodStart = periodEnd,
        outcome = outcome,
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
        val fields = PortOneBillingChargeRequest::class.members.map { it.name }.toSet()
        for (forbidden in listOf("email", "name", "phone", "phoneNumber")) {
            assertTrue(forbidden !in fields, "청구 요청에 개인정보 필드가 생겼다: $forbidden")
        }
    }
}
