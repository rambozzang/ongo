package com.ongo.application.portone

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.activitylog.ActivityLogActions
import com.ongo.application.activitylog.ActivityLogUseCase
import com.ongo.application.credit.CreditService
import com.ongo.application.payment.PaymentActivityListener
import com.ongo.application.payment.PaymentCompletedEvent
import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.CreditPackage
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.AuthProvider
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.user.User
import java.time.LocalDateTime
import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import com.ongo.domain.payment.Payment
import com.ongo.application.subscription.DummyTransactionManagerForTest
import com.ongo.application.webhook.WebhookEventRecorder
import com.ongo.application.webhook.WebhookInboundGuard
import com.ongo.domain.payment.PaymentRepository
import com.ongo.domain.subscription.SubscriptionRepository
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
import org.springframework.context.ApplicationEventPublisher
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * 브라우저가 직접 부르는 완료 엔드포인트(`complete`) 테스트.
 *
 * 웹훅 경로는 [PortOnePaymentServiceWebhookTest] 가 덮고 있었지만, 실제 구매 흐름에서
 * 먼저 도달하는 것은 이쪽이다: `usePortOne.completeResult` 가 PortOne SDK 결과를 받은 뒤
 * `POST /portone/payments/{id}/complete` 를 부른다.
 *
 * 사용자가 버튼을 두 번 누르거나, 앱이 재시도하거나, 이 호출과 웹훅이 겹치면 같은 결제가
 * 두 번 들어온다. 그때 크레딧이 두 번 나가면 안 된다.
 */
@ExtendWith(MockKExtension::class)
class PortOnePaymentServiceCompleteTest {

    @MockK private lateinit var paymentRepository: PaymentRepository
    @MockK private lateinit var subscriptionRepository: SubscriptionRepository
    @MockK private lateinit var userRepository: UserRepository
    @MockK private lateinit var creditService: CreditService
    @MockK private lateinit var gateway: PortOnePaymentGateway
    @MockK private lateinit var webhookEventRepository: WebhookEventRepository

    /** 확정 결제 퍼널 이벤트 통로. 발행 시점·내용·횟수를 여기서 고정한다. */
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
    }

    private fun creditPayment(status: PaymentStatus) = Payment(
        id = internalPaymentId,
        userId = userId,
        type = PaymentType.CREDIT,
        amount = CreditPackage.BASIC.price,
        currency = "KRW",
        status = status,
        pgProvider = "portone",
        description = "CREDIT|BASIC",
    )

    private fun paidAtGateway(amount: Int = CreditPackage.BASIC.price, currency: String = "KRW") =
        PortOnePayment(
            paymentId = portonePaymentId,
            status = "PAID",
            amount = amount,
            currency = currency,
            transactionId = "tx-1",
            paymentMethod = "CARD",
            receiptUrl = "https://receipt",
        )

    /** 정상 1회 완료. 여기서 지급이 일어나므로 프런트는 응답 직후 잔액을 다시 읽어도 된다. */
    @Test
    @DisplayName("PG 검증을 통과한 첫 완료 호출은 크레딧을 한 번 지급한다")
    fun firstCompleteGrantsOnce() {
        every { paymentRepository.findByIdForUpdate(internalPaymentId) } returns
            creditPayment(PaymentStatus.PENDING)
        every { paymentRepository.update(any()) } answers { firstArg() }
        every { gateway.getPayment(portonePaymentId) } returns paidAtGateway()
        every { creditService.addPurchasedCredits(userId, CreditPackage.BASIC, internalPaymentId) } just runs

        val result = service.complete(userId, portonePaymentId)

        assertEquals(PaymentStatus.COMPLETED.name, result.status)
        verify(exactly = 1) {
            creditService.addPurchasedCredits(userId, CreditPackage.BASIC, internalPaymentId)
        }
    }

    /**
     * **중복 완료 무지급.** 두 번째 호출은 이미 COMPLETED 인 행을 보고 조기 반환한다.
     *
     * PG 재조회조차 하지 않는 것이 중요하다 — 잠금 조회 뒤 상태를 먼저 보기 때문이며,
     * 그 순서가 뒤집히면 두 호출이 나란히 검증을 통과해 두 번 지급된다.
     */
    @Test
    @DisplayName("같은 결제로 완료를 두 번 불러도 크레딧은 한 번만 지급된다")
    fun duplicateCompleteGrantsOnce() {
        // 첫 호출은 PENDING, 두 번째 호출은 이미 COMPLETED 인 행을 돌려준다.
        every { paymentRepository.findByIdForUpdate(internalPaymentId) } returnsMany listOf(
            creditPayment(PaymentStatus.PENDING),
            creditPayment(PaymentStatus.COMPLETED),
        )
        every { paymentRepository.update(any()) } answers { firstArg() }
        every { gateway.getPayment(portonePaymentId) } returns paidAtGateway()
        every { creditService.addPurchasedCredits(userId, CreditPackage.BASIC, internalPaymentId) } just runs

        service.complete(userId, portonePaymentId)
        val second = service.complete(userId, portonePaymentId)

        assertEquals(PaymentStatus.COMPLETED.name, second.status)
        verify(exactly = 1) {
            creditService.addPurchasedCredits(userId, CreditPackage.BASIC, internalPaymentId)
        }
        // 두 번째는 PG 재조회도 하지 않는다. 상태 확인이 검증보다 먼저다.
        verify(exactly = 1) { gateway.getPayment(portonePaymentId) }
    }

    /**
     * 결제가 PG 에서 완료되지 않았으면 지급하지 않는다. 프런트에 성공을 돌려주는 대체
     * 경로를 만들지 않는다 — 실패는 실패로 보여야 한다.
     */
    @Test
    @DisplayName("PG 상태가 PAID 가 아니면 지급하지 않고 실패한다")
    fun unpaidNeverGrants() {
        every { paymentRepository.findByIdForUpdate(internalPaymentId) } returns
            creditPayment(PaymentStatus.PENDING)
        every { gateway.getPayment(portonePaymentId) } returns
            paidAtGateway().copy(status = "FAILED")

        assertThrows(IllegalArgumentException::class.java) {
            service.complete(userId, portonePaymentId)
        }

        verify(exactly = 0) { creditService.addPurchasedCredits(any(), any(), any()) }
        verify(exactly = 0) { paymentRepository.update(any()) }
    }

    @Test
    @DisplayName("환불된 결제는 늦은 완료 호출로 되살리지 않는다")
    fun refundedPaymentNeverCompletesAgain() {
        every { paymentRepository.findByIdForUpdate(internalPaymentId) } returns
            creditPayment(PaymentStatus.REFUNDED)

        assertThrows(IllegalStateException::class.java) {
            service.complete(userId, portonePaymentId)
        }

        verify(exactly = 0) { gateway.getPayment(any()) }
        verify(exactly = 0) { paymentRepository.update(any()) }
        verify(exactly = 0) { creditService.addPurchasedCredits(any(), any(), any()) }
    }

    /** 금액이 어긋나면 지급하지 않는다. 결제 금액 검증 자체는 이번 변경 대상이 아니다. */
    @Test
    @DisplayName("PG 금액이 우리 기록과 다르면 지급하지 않는다")
    fun amountMismatchNeverGrants() {
        every { paymentRepository.findByIdForUpdate(internalPaymentId) } returns
            creditPayment(PaymentStatus.PENDING)
        every { gateway.getPayment(portonePaymentId) } returns
            paidAtGateway(amount = CreditPackage.BASIC.price - 1)

        assertThrows(IllegalArgumentException::class.java) {
            service.complete(userId, portonePaymentId)
        }

        verify(exactly = 0) { creditService.addPurchasedCredits(any(), any(), any()) }
    }

    /** 남의 결제를 완료시킬 수 없다. 지급 대상이 요청자와 다르면 안 된다. */
    @Test
    @DisplayName("본인 결제가 아니면 완료할 수 없고 지급도 없다")
    fun otherUsersPaymentNeverGrants() {
        every { paymentRepository.findByIdForUpdate(internalPaymentId) } returns
            creditPayment(PaymentStatus.PENDING)

        assertThrows(IllegalStateException::class.java) {
            service.complete(userId + 1, portonePaymentId)
        }

        verify(exactly = 0) { gateway.getPayment(any()) }
        verify(exactly = 0) { creditService.addPurchasedCredits(any(), any(), any()) }
    }

    // ---- 퍼널 이벤트 ----

    /** 크레딧 결제 확정은 크레딧 전용 action 과 내부 payments.id 로 나가야 한다. */
    @Test
    @DisplayName("크레딧 결제 확정 직후 크레딧 결제 이벤트를 발행한다")
    fun creditCompletionPublishesEvent() {
        every { paymentRepository.findByIdForUpdate(internalPaymentId) } returns
            creditPayment(PaymentStatus.PENDING)
        every { paymentRepository.update(any()) } answers { firstArg() }
        every { gateway.getPayment(portonePaymentId) } returns paidAtGateway()
        every { creditService.addPurchasedCredits(userId, CreditPackage.BASIC, internalPaymentId) } just runs

        service.complete(userId, portonePaymentId)

        verify(exactly = 1) {
            eventPublisher.publishEvent(
                PaymentCompletedEvent(userId, internalPaymentId, PaymentType.CREDIT),
            )
        }
    }

    /**
     * 구독 결제는 크레딧 결제와 **다른** 이벤트여야 한다. 하나로 합치면 크레딧 재구매가
     * "체험 → 유료 전환" 비율을 부풀린다.
     */
    @Test
    @DisplayName("구독 결제 확정은 구독 결제 이벤트로 구분해 발행한다")
    fun subscriptionCompletionPublishesDistinctEvent() {
        val subscriptionPayment = Payment(
            id = internalPaymentId, userId = userId, type = PaymentType.SUBSCRIPTION,
            amount = PlanType.STARTER.price, currency = "KRW",
            status = PaymentStatus.PENDING, pgProvider = "portone",
            description = "SUBSCRIPTION|STARTER|MONTHLY",
        )
        every { paymentRepository.findByIdForUpdate(internalPaymentId) } returns subscriptionPayment
        every { paymentRepository.update(any()) } answers { firstArg() }
        every { gateway.getPayment(portonePaymentId) } returns
            paidAtGateway(amount = PlanType.STARTER.price)
        every { subscriptionRepository.findByUserId(userId) } returns
            Subscription(id = 1, userId = userId, planType = PlanType.FREE)
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { userRepository.findById(userId) } returns
            User(id = userId, email = "a@b.c", name = "t", provider = AuthProvider.GOOGLE, providerId = "g-1")
        every { userRepository.update(any()) } answers { firstArg() }
        every { creditService.applyPlanEntitlement(any(), any(), any()) } just runs

        service.complete(userId, portonePaymentId)

        verify(exactly = 1) {
            eventPublisher.publishEvent(
                PaymentCompletedEvent(userId, internalPaymentId, PaymentType.SUBSCRIPTION),
            )
        }
    }

    /**
     * 등록된 정기결제 수단이 결제 확정으로 지워지면, 다음 달 갱신이 BILLING_KEY_MISSING 이
     * 되어 방금 결제한 고객이 PAST_DUE 로 내려간다. 첫 달만 받고 자동 갱신이 끊긴다.
     */
    @Test
    @DisplayName("구독 결제 확정이 등록된 빌링키를 지우지 않는다")
    fun subscriptionCompletionPreservesBillingKey() {
        val subscriptionPayment = Payment(
            id = internalPaymentId, userId = userId, type = PaymentType.SUBSCRIPTION,
            amount = PlanType.STARTER.price, currency = "KRW",
            status = PaymentStatus.PENDING, pgProvider = "portone",
            description = "SUBSCRIPTION|STARTER|MONTHLY",
        )
        every { paymentRepository.findByIdForUpdate(internalPaymentId) } returns subscriptionPayment
        every { paymentRepository.update(any()) } answers { firstArg() }
        every { gateway.getPayment(portonePaymentId) } returns
            paidAtGateway(amount = PlanType.STARTER.price)
        every { subscriptionRepository.findByUserId(userId) } returns
            Subscription(
                id = 1, userId = userId, planType = PlanType.FREE,
                billingKeyEncrypted = "enc:already-registered",
            )
        val saved = slot<Subscription>()
        every { subscriptionRepository.update(capture(saved)) } answers { saved.captured }
        every { userRepository.findById(userId) } returns
            User(id = userId, email = "a@b.c", name = "t", provider = AuthProvider.GOOGLE, providerId = "g-1")
        every { userRepository.update(any()) } answers { firstArg() }
        every { creditService.applyPlanEntitlement(any(), any(), any()) } just runs

        service.complete(userId, portonePaymentId)

        assertEquals("enc:already-registered", saved.captured.billingKeyEncrypted)
        // 다음 청구일도 함께 채워져야 갱신 스케줄러가 이 구독을 찾는다.
        assertEquals(saved.captured.currentPeriodEnd, saved.captured.nextBillingDate)
    }

    /**
     * **기간은 뒤로 가지 않는다.**
     *
     * 갱신 정산이 기간을 늘린 뒤 늦은 웹훅이 도착하면, now 기준으로 다시 계산한 값이 이미
     * 늘어난 종료일보다 이를 수 있다. 그대로 쓰면 고객이 산 기간이 줄어든다.
     */
    @Test
    @DisplayName("이미 더 뒤인 기간은 늦은 정산이 앞당기지 못한다")
    fun settlementNeverMovesPeriodBackward() {
        val farFuture = LocalDateTime.now().plusMonths(6)
        val subscriptionPayment = Payment(
            id = internalPaymentId, userId = userId, type = PaymentType.SUBSCRIPTION,
            amount = PlanType.STARTER.price, currency = "KRW",
            status = PaymentStatus.PENDING, pgProvider = "portone",
            description = "SUBSCRIPTION_RENEWAL|STARTER|MONTHLY",
        )
        every { paymentRepository.findByIdForUpdate(internalPaymentId) } returns subscriptionPayment
        every { paymentRepository.update(any()) } answers { firstArg() }
        every { gateway.getPayment(portonePaymentId) } returns
            paidAtGateway(amount = PlanType.STARTER.price)
        every { subscriptionRepository.findByUserId(userId) } returns
            Subscription(
                id = 1, userId = userId, planType = PlanType.STARTER,
                currentPeriodEnd = farFuture,
            )
        val saved = slot<Subscription>()
        every { subscriptionRepository.update(capture(saved)) } answers { saved.captured }
        every { userRepository.findById(userId) } returns
            User(id = userId, email = "a@b.c", name = "t", provider = AuthProvider.GOOGLE, providerId = "g-1")
        every { userRepository.update(any()) } answers { firstArg() }
        every { creditService.applyPlanEntitlement(any(), any(), any()) } just runs

        service.complete(userId, portonePaymentId)

        assertEquals(farFuture, saved.captured.currentPeriodEnd)
        assertEquals(farFuture, saved.captured.nextBillingDate)
    }

    @Test
    @DisplayName("늦은 갱신 정산은 처리 시각이 아니라 기존 청구 경계에서 다음 기간을 계산한다")
    fun lateRenewalExtendsFromBillingBoundary() {
        val billingBoundary = LocalDateTime.now().minusDays(5)
        val subscriptionPayment = Payment(
            id = internalPaymentId, userId = userId, type = PaymentType.SUBSCRIPTION,
            amount = PlanType.STARTER.price, currency = "KRW",
            status = PaymentStatus.PENDING, pgProvider = "portone",
            description = "SUBSCRIPTION_RENEWAL|STARTER|MONTHLY",
        )
        every { paymentRepository.findByIdForUpdate(internalPaymentId) } returns subscriptionPayment
        every { paymentRepository.update(any()) } answers { firstArg() }
        every { gateway.getPayment(portonePaymentId) } returns
            paidAtGateway(amount = PlanType.STARTER.price)
        every { subscriptionRepository.findByUserId(userId) } returns
            Subscription(
                id = 1, userId = userId, planType = PlanType.STARTER,
                currentPeriodEnd = billingBoundary,
                nextBillingDate = billingBoundary,
            )
        val saved = slot<Subscription>()
        every { subscriptionRepository.update(capture(saved)) } answers { saved.captured }
        every { userRepository.findById(userId) } returns
            User(id = userId, email = "a@b.c", name = "t", provider = AuthProvider.GOOGLE, providerId = "g-1")
        every { userRepository.update(any()) } answers { firstArg() }
        every { creditService.applyPlanEntitlement(any(), any(), any()) } just runs

        service.complete(userId, portonePaymentId)

        assertEquals(billingBoundary.plusMonths(1), saved.captured.currentPeriodEnd)
        assertEquals(billingBoundary, saved.captured.currentPeriodStart)
        assertEquals(saved.captured.currentPeriodEnd, saved.captured.nextBillingDate)
    }

    /** 갱신 결제도 일반 구독 결제와 같은 규칙으로 정산된다 — 크레딧 권한 포함. */
    @Test
    @DisplayName("갱신 결제 정산도 플랜 권한을 적용한다")
    fun renewalSettlementAppliesEntitlement() {
        val subscriptionPayment = Payment(
            id = internalPaymentId, userId = userId, type = PaymentType.SUBSCRIPTION,
            amount = PlanType.STARTER.price, currency = "KRW",
            status = PaymentStatus.PENDING, pgProvider = "portone",
            description = "SUBSCRIPTION_RENEWAL|STARTER|MONTHLY",
        )
        every { paymentRepository.findByIdForUpdate(internalPaymentId) } returns subscriptionPayment
        every { paymentRepository.update(any()) } answers { firstArg() }
        every { gateway.getPayment(portonePaymentId) } returns
            paidAtGateway(amount = PlanType.STARTER.price)
        every { subscriptionRepository.findByUserId(userId) } returns
            Subscription(id = 1, userId = userId, planType = PlanType.STARTER)
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { userRepository.findById(userId) } returns
            User(id = userId, email = "a@b.c", name = "t", provider = AuthProvider.GOOGLE, providerId = "g-1")
        every { userRepository.update(any()) } answers { firstArg() }
        every { creditService.applyPlanEntitlement(any(), any(), any()) } just runs

        service.complete(userId, portonePaymentId)

        verify(exactly = 1) {
            creditService.applyPlanEntitlement(userId, PlanType.STARTER, "SUBSCRIPTION_PAID")
        }
    }

    /** 재완료는 조기 반환한다 — 추가 이벤트가 나가면 결제 한 건이 여러 번 세어진다. */
    @Test
    @DisplayName("이미 완료된 결제를 다시 완료해도 이벤트는 추가로 나가지 않는다")
    fun duplicateCompleteDoesNotPublishAgain() {
        every { paymentRepository.findByIdForUpdate(internalPaymentId) } returnsMany listOf(
            creditPayment(PaymentStatus.PENDING),
            creditPayment(PaymentStatus.COMPLETED),
        )
        every { paymentRepository.update(any()) } answers { firstArg() }
        every { gateway.getPayment(portonePaymentId) } returns paidAtGateway()
        every { creditService.addPurchasedCredits(userId, CreditPackage.BASIC, internalPaymentId) } just runs

        service.complete(userId, portonePaymentId)
        service.complete(userId, portonePaymentId)

        verify(exactly = 1) { eventPublisher.publishEvent(any<PaymentCompletedEvent>()) }
    }

    /**
     * 권한 반영이 실패하면 결제는 완료로 세어지면 안 된다.
     *
     * `CREDIT|` 형식이 아닌 결제는 패키지를 정할 수 없어 지급 단계에서 던진다. 그때
     * 이벤트가 나가면 크레딧을 못 받은 결제가 퍼널에서는 성공으로 잡힌다.
     */
    @Test
    @DisplayName("권한 반영에 실패하면 이벤트를 발행하지 않는다")
    fun failedEntitlementPublishesNothing() {
        every { paymentRepository.findByIdForUpdate(internalPaymentId) } returns
            creditPayment(PaymentStatus.PENDING).copy(description = "스타터 팩 (500 크레딧)")
        every { paymentRepository.update(any()) } answers { firstArg() }
        every { gateway.getPayment(portonePaymentId) } returns paidAtGateway()

        assertThrows(IllegalStateException::class.java) {
            service.complete(userId, portonePaymentId)
        }

        verify(exactly = 0) { eventPublisher.publishEvent(any<PaymentCompletedEvent>()) }
    }

    /** PG 검증 실패도 마찬가지다. 승인되지 않은 결제가 퍼널에 남으면 안 된다. */
    @Test
    @DisplayName("PG 검증에 실패하면 이벤트를 발행하지 않는다")
    fun unverifiedPaymentPublishesNothing() {
        every { paymentRepository.findByIdForUpdate(internalPaymentId) } returns
            creditPayment(PaymentStatus.PENDING)
        every { gateway.getPayment(portonePaymentId) } returns paidAtGateway().copy(status = "FAILED")

        assertThrows(IllegalArgumentException::class.java) {
            service.complete(userId, portonePaymentId)
        }

        verify(exactly = 0) { eventPublisher.publishEvent(any<PaymentCompletedEvent>()) }
    }
}

/**
 * 커밋 뒤 기록을 맡는 리스너.
 *
 * 스프링 컨텍스트 없이 검증한다 — `@TransactionalEventListener` 는 **언제 불릴지**만
 * 정하고, 무엇을 기록할지는 이 메서드의 평범한 코드다. 호출 시점 계약(커밋 후, 재완료
 * 시 미발행)은 위 발행 쪽 테스트가 따로 고정한다.
 */
@ExtendWith(MockKExtension::class)
class PaymentActivityListenerTest {

    private val activityLogUseCase = mockk<ActivityLogUseCase>(relaxed = true)
    private val listener = PaymentActivityListener(activityLogUseCase)

    private val userId = 7L
    private val paymentId = 42L

    /**
     * **신뢰성 경계.** 결제 트랜잭션에 묶이는 `logActivity` 가 아니라, `REQUIRES_NEW` 로
     * 자체 트랜잭션을 열고 실패를 삼키는 경로여야 한다. 승인된 결제가 기록 실패로
     * 롤백되면 고객은 돈만 내고 권한을 못 받는다.
     */
    @Test
    @DisplayName("크레딧 결제 이벤트를 독립 트랜잭션 기록으로 남긴다")
    fun recordsCreditPaymentIndependently() {
        listener.onPaymentCompleted(PaymentCompletedEvent(userId, paymentId, PaymentType.CREDIT))

        verify(exactly = 1) {
            activityLogUseCase.logActivityIndependently(
                userId = userId,
                action = ActivityLogActions.PAYMENT_CREDIT_COMPLETED,
                entityType = ActivityLogActions.ENTITY_PAYMENT,
                entityId = paymentId,
            )
        }
        // 결제 트랜잭션에 묶이는 경로는 쓰지 않는다.
        verify(exactly = 0) { activityLogUseCase.logActivity(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    @DisplayName("구독 결제 이벤트는 구독 전용 action 으로 남긴다")
    fun recordsSubscriptionPaymentWithDistinctAction() {
        listener.onPaymentCompleted(
            PaymentCompletedEvent(userId, paymentId, PaymentType.SUBSCRIPTION),
        )

        verify(exactly = 1) {
            activityLogUseCase.logActivityIndependently(
                userId = userId,
                action = ActivityLogActions.PAYMENT_SUBSCRIPTION_COMPLETED,
                entityType = ActivityLogActions.ENTITY_PAYMENT,
                entityId = paymentId,
            )
        }
    }

    /**
     * 기록에 개인정보·PG 원문이 섞이면 안 된다. 이벤트가 그런 값을 아예 들고 있지 않으므로
     * 기록 호출에도 들어갈 수 없다는 것을 필드 목록으로 고정한다.
     */
    @Test
    @DisplayName("이벤트는 식별자만 담고 개인정보나 PG 원문을 담지 않는다")
    fun eventCarriesNoSensitiveValues() {
        val fields = PaymentCompletedEvent::class.java.declaredFields
            .map { it.name }
            .filterNot { it == "\$stable" }
            .toSet()

        assertEquals(setOf("userId", "paymentId", "type"), fields)
    }

    /**
     * **핵심 신뢰성 경계.** 기록이 던져도 리스너 밖으로 예외가 나가면 안 된다.
     *
     * 커밋 뒤 콜백에서 예외가 새면 결제 트랜잭션은 롤백되지 않지만, 호출 스택(웹훅
     * 핸들러 또는 `complete` 응답)으로 전파된다. 웹훅이 5xx 를 받으면 포트원이 재전송하고,
     * `complete` 응답이 깨지면 결제한 사용자가 실패 화면을 본다 — 둘 다 이미 승인·지급이
     * 끝난 결제를 실패처럼 보이게 만든다.
     *
     * 협력자의 내부 삼킴에 기대지 않고 **리스너 경계에서** 막는지 본다. 그래서 mock 은
     * `throws` 만 설정하고 되돌리지 않는다.
     */
    @Test
    @DisplayName("기록이 예외를 던져도 리스너 밖으로 나가지 않는다")
    fun recordingFailureNeverEscapes() {
        val failing = mockk<ActivityLogUseCase>()
        every {
            failing.logActivityIndependently(any(), any(), any(), any())
        } throws RuntimeException("활동 로그 저장 실패")

        assertDoesNotThrow {
            PaymentActivityListener(failing)
                .onPaymentCompleted(PaymentCompletedEvent(userId, paymentId, PaymentType.CREDIT))
        }

        // 기록을 시도는 했다 — 조용히 건너뛴 것이 아니라 실패를 흡수한 것이다.
        verify(exactly = 1) { failing.logActivityIndependently(any(), any(), any(), any()) }
    }
}

/**
 * 운영 문서 `docs/operations/FUNNEL_MEASUREMENT_QUERIES.md` 의 SQL 자체 정합성 가드.
 *
 * ## 왜 필요한가
 *
 * 실제로 사고가 났다. 2번 쿼리의 CTE 가 `paid_at` 을
 * `MIN(...) FILTER (WHERE action = 'PAYMENT_SUBSCRIPTION_COMPLETED')` 로 계산하는데,
 * 같은 쿼리의 `WHERE action IN (...)` 목록에는 그 action 이 없었다. 바깥 필터가 그 행을
 * 먼저 걸러내므로 `paid_at` 은 **항상 NULL** 이고 `paid_after_run` 은 **항상 0** 이었다.
 *
 * SQL 은 문법적으로 완벽했고 실행해도 오류가 나지 않는다. 조용히 0 을 돌려줄 뿐이라,
 * 운영자는 "아무도 결제하지 않았다"로 읽게 된다. 문서 리뷰로는 잡기 어렵다 —
 * 두 목록이 30 줄 떨어져 있고 들여쓰기도 다르다.
 *
 * ## 왜 이 파일에 있는가
 *
 * 이 문서가 세는 결제 action 이 여기서 도입됐고, 같은 파일에 리스너 가드가 이미 있다.
 * 문서 전용 테스트 파일을 따로 두는 편이 이상적이지만, 이번 작업은 기존 파일 편집만
 * 허용돼 있어 가장 관련 있는 곳에 붙인다.
 *
 * ## 실행 시 주의 — Gradle 이 문서 변경을 모른다
 *
 * 이 테스트는 저장소의 마크다운을 읽는데, 그 파일은 `test` 태스크의 입력으로 선언돼
 * 있지 않다. 따라서 **문서만 고치고 다시 돌리면 태스크가 UP-TO-DATE 로 스킵되어 옛
 * 결과가 그대로 통과한다.** 문서를 고친 뒤 검증할 때는 `--rerun-tasks` 를 붙이거나
 * `cleanTest` 를 먼저 돌려야 한다. 실제로 이 가드의 변이 검증에서 한 번 잘못 통과했다.
 */
class FunnelMeasurementDocTest {

    private val doc: String = run {
        // 모듈 디렉터리에서 실행되든 저장소 루트에서 실행되든 찾도록 위로 올라간다.
        var dir: java.io.File? = java.io.File(System.getProperty("user.dir")).absoluteFile
        val relative = "docs/operations/FUNNEL_MEASUREMENT_QUERIES.md"
        while (dir != null && !java.io.File(dir, relative).isFile) dir = dir.parentFile
        val found = dir?.let { java.io.File(it, relative) }
        requireNotNull(found) { "운영 문서를 찾을 수 없다: $relative" }
        found.readText()
    }

    /** ```sql ... ``` 블록 하나하나를 독립된 쿼리로 본다. */
    private fun sqlBlocks(): List<String> =
        Regex("```sql\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
            .findAll(doc)
            .map { it.groupValues[1] }
            .toList()

    private fun filteredActions(sql: String): Set<String> =
        Regex("""FILTER\s*\(\s*WHERE\s+action\s*=\s*'([A-Z_]+)'""")
            .findAll(sql).map { it.groupValues[1] }.toSet()

    /**
     * 한 SQL 블록에 CTE 가 여럿이면 `action IN` 목록도 여럿이다. 전부 모아 합집합으로 본다
     * — 하나만 읽으면 뒤쪽 CTE 의 목록을 놓쳐 있는 것을 없다고 판정한다.
     */
    private fun listedActions(sql: String): Set<String> =
        Regex("""action\s+IN\s*\(([^)]*)\)""", RegexOption.IGNORE_CASE)
            .findAll(sql)
            .flatMap { Regex("'([A-Z_]+)'").findAll(it.groupValues[1]).map { m -> m.groupValues[1] } }
            .toSet()

    /** `<이름> AS (` 로 시작하는 CTE 본문을 이름으로 꺼낸다. */
    private fun cte(sql: String, name: String): String {
        val start = sql.indexOf("$name AS (")
        require(start >= 0) { "CTE 를 찾을 수 없다: $name" }
        // CTE 는 여는 괄호와 짝이 맞는 위치에서 끝난다.
        var depth = 0
        var i = sql.indexOf('(', start)
        val from = i
        while (i < sql.length) {
            when (sql[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return sql.substring(from, i + 1)
                }
            }
            i++
        }
        error("CTE 괄호가 닫히지 않았다: $name")
    }

    @Test
    @DisplayName("문서를 실제로 읽었고 SQL 블록이 존재한다")
    fun docIsReadable() {
        // 정규식이 아무것도 못 잡아 아래 검사들이 공허하게 통과하는 것을 막는다.
        assertTrue(doc.isNotBlank(), "문서가 비어 있다")
        assertTrue(sqlBlocks().size >= 2, "SQL 블록이 2개 미만이다: ${sqlBlocks().size}")
    }

    /**
     * **핵심 가드.** `FILTER (WHERE action = X)` 로 값을 뽑는 쿼리는 바깥 `action IN` 에도
     * X 를 넣어야 한다. 아니면 그 열은 조용히 항상 NULL 이 된다.
     */
    @Test
    @DisplayName("CTE 가 FILTER 로 쓰는 action 은 모두 action IN 목록에 있다")
    fun everyFilteredActionIsAlsoSelected() {
        sqlBlocks().forEachIndexed { index, sql ->
            val filtered = filteredActions(sql)
            if (filtered.isEmpty()) return@forEachIndexed

            val listed = listedActions(sql)
            val missing = filtered - listed
            assertTrue(
                missing.isEmpty(),
                "SQL 블록 #${index + 1}: FILTER 로 쓰는데 action IN 목록에 없다 → " +
                    "$missing. 이 열은 항상 NULL 이 되고 집계가 조용히 0 이 된다.",
            )
        }
    }

    /** 결제 전환 칸이 실제로 문서에 살아 있는지 — 위 가드가 빈 집합으로 통과하지 않게 한다. */
    @Test
    @DisplayName("진행 쿼리는 구독 결제 확정을 FILTER 와 목록 양쪽에서 쓴다")
    fun subscriptionPaymentIsWiredIntoTheProgressQuery() {
        // 1번 쿼리는 단순 집계라 FILTER 가 없다. 순서 조건을 세는 CTE 를 골라야 한다.
        val progress = sqlBlocks().first { filteredActions(it).isNotEmpty() }

        assertTrue(
            ActivityLogActions.PAYMENT_SUBSCRIPTION_COMPLETED in filteredActions(progress),
            "구독 결제 확정을 FILTER 로 쓰지 않는다",
        )
        assertTrue(
            ActivityLogActions.PAYMENT_SUBSCRIPTION_COMPLETED in listedActions(progress),
            "구독 결제 확정이 action IN 목록에 없다",
        )
    }

    /**
     * 첫 클립 가용 칸도 같은 방식으로 살아 있어야 한다. 이 칸이 빠지면 "실행을 만들었지만
     * 결과물이 안 나온" 구간의 손실이 보이지 않는다.
     */
    @Test
    @DisplayName("진행 쿼리는 클립 가용을 FILTER 와 목록 양쪽에서 쓴다")
    fun clipAvailabilityIsWiredIntoTheProgressQuery() {
        val progress = sqlBlocks().first { filteredActions(it).isNotEmpty() }

        assertTrue(
            ActivityLogActions.SHORTS_CLIP_AVAILABLE in filteredActions(progress),
            "클립 가용을 FILTER 로 쓰지 않는다",
        )
        assertTrue(
            ActivityLogActions.SHORTS_CLIP_AVAILABLE in listedActions(progress),
            "클립 가용이 action IN 목록에 없다",
        )
    }

    /**
     * 클립 가용은 클립마다·재시도마다 남는다. 집계에서 `MIN` 으로 접지 않으면 한 사람이
     * 여러 번 세어져 깔때기가 부풀어 오른다. 문서가 그 접기를 실제로 하고 있는지 본다.
     */
    @Test
    @DisplayName("진행 쿼리는 클립 가용을 MIN 으로 접는다")
    fun clipAvailabilityIsFoldedWithMin() {
        val progress = sqlBlocks().first { filteredActions(it).isNotEmpty() }

        assertTrue(
            Regex(
                """MIN\s*\(\s*created_at\s*\)\s*FILTER\s*\(\s*WHERE\s+action\s*=\s*'${ActivityLogActions.SHORTS_CLIP_AVAILABLE}'""",
            ).containsMatchIn(progress),
            "클립 가용을 MIN 으로 접지 않는다 — 재렌더·다중 클립이 중복 집계된다",
        )
    }

    /**
     * **실행 단위 집계 가드.**
     *
     * 실행 생성과 클립 가용을 사용자 단위로 바로 접으면 A 실행의 생성 시각과 B 실행의
     * 가용 시각이 짝지어져, 결과물을 낸 적 없는 실행이 결과를 낸 것처럼 보인다.
     * 두 사건은 반드시 **같은 실행 안에서** 먼저 묶여야 한다.
     */
    @Test
    @DisplayName("실행 범위 CTE 는 사용자와 실행 식별자로 함께 묶는다")
    fun runScopedCteGroupsByUserAndRun() {
        val progress = sqlBlocks().first { filteredActions(it).isNotEmpty() }
        val runScoped = cte(progress, "run_scoped")

        assertTrue(
            Regex("""GROUP\s+BY\s+user_id\s*,\s*entity_id""").containsMatchIn(runScoped),
            "실행 범위 CTE 가 user_id, entity_id 로 묶지 않는다 — 실행 간 시각이 섞인다",
        )
        // 두 사건이 같은 entity 계열임을 전제로 묶으므로, 그 전제를 필터로 못 박아야 한다.
        assertTrue(
            "entity_type = '${ActivityLogActions.ENTITY_SHORTS_RUN}'" in runScoped,
            "실행 범위 CTE 가 entity_type 을 shorts_run 으로 좁히지 않는다",
        )
    }

    /**
     * 실행 범위 CTE 가 다루는 action 은 **entity_id 가 runId 인 둘뿐**이어야 한다.
     * 결제 사건의 entity_id 는 `payments.id` 라 같은 GROUP BY 에 섞이면 실행처럼 묶인다.
     */
    @Test
    @DisplayName("실행 범위 CTE 는 실행 식별자를 쓰는 두 action 만 다룬다")
    fun runScopedCteExcludesPaymentActions() {
        val progress = sqlBlocks().first { filteredActions(it).isNotEmpty() }
        val runScoped = cte(progress, "run_scoped")

        assertEquals(
            setOf(ActivityLogActions.SHORTS_RUN_CREATED, ActivityLogActions.SHORTS_CLIP_AVAILABLE),
            listedActions(runScoped),
            "실행 범위 CTE 의 action 목록이 실행 단위 두 사건과 다르다",
        )
        assertTrue(
            ActivityLogActions.PAYMENT_SUBSCRIPTION_COMPLETED !in runScoped,
            "결제 사건이 실행 범위 CTE 에 섞였다 — entity_id 가 payments.id 라 실행으로 묶을 수 없다",
        )
    }

    /**
     * 같은 실행 안에서 생성 뒤에 가용이 온 경우만 인정해야 한다. 이 조건이 빠지면
     * 실행 범위로 묶은 의미가 사라진다.
     */
    @Test
    @DisplayName("사용자 단위 접기는 실행 내 순서 조건을 유지한다")
    fun userFoldKeepsWithinRunOrdering() {
        val progress = sqlBlocks().first { filteredActions(it).isNotEmpty() }
        val afterTrial = cte(progress, "runs_after_trial")

        assertTrue(
            Regex("""FILTER\s*\(\s*WHERE\s+r?\.?available_at\s*>\s*r?\.?run_at\s*\)""")
                .containsMatchIn(afterTrial),
            "실행 내 available_at > run_at 조건이 없다",
        )
    }

    /**
     * **체험 기준선 가드.**
     *
     * 사용자 단위로 `MIN(run_at)` 을 먼저 구하고 나중에 체험 시각과 비교하면, 체험
     * **전에** 만들어 실패한 실행이 최솟값을 차지해 체험 후의 실제 성공이 통째로
     * 누락된다. 필터는 반드시 집계 **전에**, 실행 단위로 걸려야 한다.
     */
    @Test
    @DisplayName("실행 집계는 체험 시각과 결합해 체험 이후 실행만 남긴다")
    fun runAggregationIsScopedToPostTrialRuns() {
        val progress = sqlBlocks().first { filteredActions(it).isNotEmpty() }
        val afterTrial = cte(progress, "runs_after_trial")

        // 실행 범위 결과를 사용자 단위 사건과 결합한다.
        assertTrue(
            Regex("""JOIN\s+user_events""").containsMatchIn(afterTrial),
            "실행 집계가 user_events 와 결합되지 않는다 — 체험 기준선을 알 수 없다",
        )
        // 그리고 GROUP BY 앞에서 체험 이후로 좁힌다.
        val whereBeforeGroup = afterTrial.substringBefore("GROUP BY")
        assertTrue(
            Regex("""WHERE\s+r?\.?run_at\s*>\s*e?\.?trial_at""").containsMatchIn(whereBeforeGroup),
            "run_at > trial_at 이 집계 전에 걸리지 않는다",
        )
    }

    /**
     * 체험과 무관하게 접은 옛 구문이 남아 있으면, 위 가드를 통과하면서도 최종 SELECT 가
     * 그쪽을 쓸 수 있다. 이름 자체를 문서에서 몰아낸다.
     */
    @Test
    @DisplayName("체험과 무관한 사용자 단위 첫 실행 구문이 남아 있지 않다")
    fun noTrialAgnosticFirstRunRemains() {
        val progress = sqlBlocks().first { filteredActions(it).isNotEmpty() }

        assertTrue("user_runs" !in progress, "체험과 무관한 user_runs CTE 가 남아 있다")
        assertTrue("first_run_at" !in progress, "체험과 무관한 first_run_at 이 남아 있다")
        assertTrue("first_available_at" !in progress, "체험과 무관한 first_available_at 이 남아 있다")
    }

    /**
     * 문서의 action 문자열이 코드 상수와 일치하는지 본다. 상수 이름이 바뀌면 문서가
     * 조용히 과거 이름을 세게 되고, 그 쿼리는 오류 없이 0 을 돌려준다.
     */
    @Test
    @DisplayName("문서가 쓰는 action 은 모두 코드에 존재하는 상수다")
    fun documentedActionsExistInCode() {
        val known = setOf(
            ActivityLogActions.SHORTS_RUN_BLOCKED_INSUFFICIENT_CREDIT,
            ActivityLogActions.SUBSCRIPTION_TRIAL_STARTED,
            ActivityLogActions.SHORTS_RUN_CREATED,
            ActivityLogActions.SHORTS_CLIP_AVAILABLE,
            ActivityLogActions.PAYMENT_SUBSCRIPTION_COMPLETED,
            ActivityLogActions.PAYMENT_CREDIT_COMPLETED,
        )
        val used = sqlBlocks().flatMap { filteredActions(it) + listedActions(it) }.toSet()

        assertTrue(used.isNotEmpty(), "문서에서 action 을 하나도 못 읽었다")
        assertEquals(emptySet<String>(), used - known, "문서에만 있고 코드에 없는 action")
    }
}
