package com.ongo.application.subscription

import com.ongo.application.credit.CreditService
import com.ongo.application.portone.PortOnePayment
import com.ongo.application.portone.PortOnePaymentGateway
import com.ongo.application.portone.PortOnePaymentService
import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.notification.NotificationRepository
import com.ongo.domain.payment.Payment
import com.ongo.domain.payment.PaymentRepository
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRenewalAttemptRepository
import com.ongo.domain.subscription.SubscriptionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 레거시 Paddle 구독이 **PortOne 자동 갱신을 타지 않는지** 고정한다.
 *
 * ## 무엇이 위험한가
 *
 * [SubscriptionRenewalService] 는 PortOne 빌링키로 청구한다. 그런데 Paddle 로 결제한 구독도
 * `status=ACTIVE` 이고 `next_billing_date` 가 채워져 있어(Paddle 의 `next_billed_at` 을 그대로
 * 저장한다) 갱신 조회 조건에 그대로 걸린다. 걸리면 어느 쪽으로 가든 고객이 손해를 본다.
 *
 *  - 빌링키가 없으면 → `BILLING_KEY_MISSING` → `PAST_DUE` → 7일 뒤 Free 강등.
 *    **Paddle 에서는 정상 결제 중인데 우리 쪽에서만 권한을 뺏는다.**
 *  - 빌링키가 있으면 → Paddle 과 PortOne 이 같은 주기를 각각 청구한다. **이중 청구다.**
 *
 * 이 결함은 `subscription.renewal.enabled` 를 켠 뒤에야 드러난다. 켜는 날 처음 알게 되는
 * 종류의 사고라 코드로 고정한다.
 *
 * ## 왜 저장소 조건만으로 부족한가
 *
 * `SubscriptionJooqRepository.findDueForBilling` 이 이미 같은 조건으로 거른다. 그래도 서비스
 * 진입부에 한 겹 더 두는 이유는, 그 쿼리를 지나지 않는 호출자(운영 도구·재처리 배치·앞으로
 * 생길 코드)가 **돈이 움직이는 경로를 우회**할 수 있기 때문이다.
 *
 * **실제 PortOne 호출은 하지 않는다.** gateway 는 mock 이며, 이 파일의 핵심 단언은 그 mock 이
 * **한 번도 불리지 않는다**는 것이다.
 */
class PaddleSubscriptionRenewalGuardTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true)
    private val renewalAttemptRepository = mockk<SubscriptionRenewalAttemptRepository>(relaxed = true)
    private val notificationRepository = mockk<NotificationRepository>(relaxed = true)
    private val gateway = mockk<PortOnePaymentGateway>(relaxed = true)
    private val tokenEncryptionPort = mockk<TokenEncryptionPort>(relaxed = true)
    private val paymentRepository = mockk<PaymentRepository>(relaxed = true)
    private val paymentService = mockk<PortOnePaymentService>(relaxed = true)
    private val creditService = mockk<CreditService>(relaxed = true)

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

    private val internalPaymentId = 4242L
    private val now: LocalDateTime = LocalDateTime.parse("2026-09-01T02:00:00")
    private val periodEnd: LocalDateTime = LocalDateTime.parse("2026-09-01T00:00:00")

    /**
     * PortOne 구독 픽스처. `paddleSubscriptionId` 만 바꿔 두 경로를 비교한다 —
     * 다른 조건이 함께 달라지면 무엇이 결과를 갈랐는지 알 수 없다.
     */
    private fun subscription(paddleSubscriptionId: String?) = Subscription(
        id = 5L,
        userId = 7L,
        planType = PlanType.PRO,
        status = SubscriptionStatus.ACTIVE,
        price = 19_900,
        billingCycle = BillingCycle.MONTHLY,
        currentPeriodStart = periodEnd.minusMonths(1),
        currentPeriodEnd = periodEnd,
        nextBillingDate = periodEnd,
        billingKeyEncrypted = "enc-key",
        paddleSubscriptionId = paddleSubscriptionId,
    )

    @BeforeEach
    fun stubHappyPath() {
        every { creditService.ensureAccountPresence(any()) } returns Unit
        every { renewalAttemptRepository.claimPeriod(any()) } returns 99L
        every { paymentRepository.save(any()) } answers {
            firstArg<Payment>().copy(id = internalPaymentId)
        }
        every { paymentRepository.update(any()) } answers { firstArg() }
        every { tokenEncryptionPort.decrypt(EncryptedToken("enc-key")) } returns PlainToken("bk_live")
        every { gateway.payWithBillingKey(any()) } returns PortOnePayment(
            paymentId = "sub-5-renew-2026-09-01",
            status = "PAID",
            amount = 19_900,
            currency = "KRW",
            transactionId = "tx",
            paymentMethod = "card",
            receiptUrl = null,
        )
    }

    /* ── Paddle 구독: 아무것도 하지 않는다 ──────────────────────────── */

    /**
     * **핵심.** PG 를 부르지 않는다. 부르면 Paddle 과 같은 주기를 두 번 청구하게 된다.
     */
    @Test
    @DisplayName("Paddle 구독은 PortOne 게이트웨이를 호출하지 않는다")
    fun paddleSubscriptionNeverCallsGateway() {
        service.renew(subscription(paddleSubscriptionId = "sub_paddle_1"), now)

        verify(exactly = 0) { gateway.payWithBillingKey(any()) }
        // 빌링키 복호화까지 가지 않는다 — 청구 준비 자체를 시작하지 않는다.
        verify(exactly = 0) { tokenEncryptionPort.decrypt(any()) }
    }

    /**
     * **핵심.** 상태를 건드리지 않는다. 주기를 선점하거나 결제 원장을 만들면, 청구하지
     * 않았는데 "시도했다"는 흔적이 남아 다음 실행이 Stale 로 재조회한다.
     */
    @Test
    @DisplayName("Paddle 구독은 어떤 상태도 바꾸지 않는다")
    fun paddleSubscriptionChangesNoState() {
        service.renew(subscription(paddleSubscriptionId = "sub_paddle_1"), now)

        verify(exactly = 0) { renewalAttemptRepository.claimPeriod(any()) }
        verify(exactly = 0) { paymentRepository.save(any()) }
        verify(exactly = 0) { subscriptionRepository.update(any()) }
        verify(exactly = 0) { notificationRepository.save(any()) }
        verify(exactly = 0) { paymentService.complete(any(), any()) }
    }

    /**
     * 가드가 **맨 앞**에 있는지 본다. 뒤로 밀리면 그 앞의 조회·검사가 먼저 돌고, 언젠가
     * 그중 하나가 부작용을 갖게 될 때 조용히 새어 나간다.
     */
    @Test
    @DisplayName("Paddle 구독은 크레딧 원장 확인조차 하지 않는다")
    fun paddleSubscriptionSkipsBeforeAnyLookup() {
        service.renew(subscription(paddleSubscriptionId = "sub_paddle_1"), now)

        verify(exactly = 0) { creditService.ensureAccountPresence(any()) }
    }

    /** 건너뛴 것이지 실패한 것이 아니다. 예외를 던지면 스케줄러 로그가 오류로 덮인다. */
    @Test
    @DisplayName("Paddle 구독은 예외 없이 건너뛴다")
    fun paddleSubscriptionSkipsWithoutThrowing() {
        assertNull(service.renew(subscription(paddleSubscriptionId = "sub_paddle_1"), now))
    }

    /* ── PortOne 구독: 종전 그대로 갱신한다 ────────────────────────── */

    /**
     * **가드가 너무 넓지 않은지.** 이것이 없으면 "전부 건너뛴다"로 바꿔도 위 테스트들이
     * 모두 통과한다 — 자동 갱신 전체를 죽이는 변경을 잡지 못한다.
     */
    @Test
    @DisplayName("Paddle 식별자가 없는 구독은 종전대로 청구한다")
    fun portOneSubscriptionStillRenews() {
        val outcome = service.renew(subscription(paddleSubscriptionId = null), now)

        assertNotNull(outcome, "PortOne 구독의 갱신이 막혔다")
        verify(exactly = 1) { gateway.payWithBillingKey(any()) }
    }

    /**
     * 빈 문자열은 Paddle 구독이 아니다. 식별자가 없다는 뜻이므로 종전대로 청구한다 —
     * 여기서 막으면 데이터 정리 과정에서 빈 값이 들어간 정상 고객의 갱신이 멈춘다.
     */
    @Test
    @DisplayName("Paddle 식별자가 빈 문자열이면 정상 구독으로 본다")
    fun blankPaddleIdIsNotAPaddleSubscription() {
        val outcome = service.renew(subscription(paddleSubscriptionId = "   "), now)

        assertNotNull(outcome)
        verify(exactly = 1) { gateway.payWithBillingKey(any()) }
    }
}
