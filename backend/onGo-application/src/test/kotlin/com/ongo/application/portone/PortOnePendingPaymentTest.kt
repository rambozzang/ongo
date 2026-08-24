package com.ongo.application.portone

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AuthProvider
import com.ongo.common.enums.CreditPackage
import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import com.ongo.domain.payment.Payment
import com.ongo.domain.payment.PaymentRepository
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.User
import com.ongo.domain.user.UserRepository
import com.ongo.domain.webhook.WebhookEventRepository
import com.ongo.application.payment.PaymentCompletedEvent
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * 미확정(PENDING) 결제 행의 진실성을 고정한다.
 *
 * 체크아웃은 결제창을 열기 **전에** PENDING 행을 만든다. 사용자가 PG 창을 닫거나
 * 브라우저를 종료하면 `complete` 가 불리지 않아 그 행은 PENDING 으로 남는다.
 *
 * 여기서 지키는 것은 두 가지다.
 * 1. 체크아웃만으로는 결제가 성립하지 않는다(PENDING, 크레딧 지급 없음).
 * 2. 아무도 그 행을 **추측으로** 실패·취소 처리하지 않는다. PG 에 묻지 않은 상태 변경은
 *    사용자가 실제로 결제했는데 실패로 보이게 만들 수 있다.
 */
@ExtendWith(MockKExtension::class)
class PortOnePendingPaymentTest {

    @MockK private lateinit var paymentRepository: PaymentRepository
    @MockK private lateinit var subscriptionRepository: SubscriptionRepository
    @MockK private lateinit var userRepository: UserRepository
    @MockK private lateinit var creditService: CreditService
    @MockK private lateinit var gateway: PortOnePaymentGateway
    @MockK private lateinit var webhookEventRepository: WebhookEventRepository

    /** 확정 결제 이벤트 발행 통로. 미확정 단계에서는 아무것도 나오지 않아야 한다. */
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private lateinit var service: PortOnePaymentService

    private val userId = 7L

    private val webhookId = "webhook-1"
    private val signature = "v1,signature"
    private val timestamp = "1700000000"

    /** 포트원이 보내는 웹훅 본문. `PortOnePaymentServiceWebhookTest` 와 같은 형식이다. */
    private fun body(type: String, paymentId: String = "ongo-42") =
        """{"type":"$type","timestamp":"2026-08-05T10:00:00Z","data":{"paymentId":"$paymentId","storeId":"store-test"}}"""

    private fun allowSignature(valid: Boolean = true) {
        every { gateway.verifyWebhookSignature(any(), any(), any(), any()) } returns valid
    }

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
        )
    }

    /**
     * 결제창을 열기 전 단계다. 이 시점에 COMPLETED 로 만들면 결제하지 않은 사용자가
     * 완료로 보이고, 크레딧을 지급하면 무료로 나간다.
     */
    @Test
    @DisplayName("크레딧 체크아웃은 PENDING 행만 만들고 크레딧을 지급하지 않는다")
    fun checkoutOnlyCreatesPending() {
        every { userRepository.findById(userId) } returns
            User(
                id = userId, email = "a@b.c", name = "tester",
                provider = AuthProvider.GOOGLE, providerId = "google-1",
            )
        val saved = slot<Payment>()
        every { paymentRepository.save(capture(saved)) } answers {
            saved.captured.copy(id = 42)
        }

        service.createCreditCheckout(userId, CreditPackage.BASIC.name)

        assertEquals(PaymentStatus.PENDING, saved.captured.status)
        assertEquals(CreditPackage.BASIC.price, saved.captured.amount)
        verify(exactly = 0) { creditService.addPurchasedCredits(any(), any(), any()) }
    }

    /**
     * 사용자가 결제창을 닫으면 프런트는 `complete` 를 부르지 않는다. 서버는 그 사실을
     * 알 수 없으므로 행은 PENDING 그대로다.
     *
     * **이 상태를 실패로 바꾸는 코드가 있으면 안 된다.** 서버에는 "사용자가 취소함"과
     * "웹훅이 아직 안 옴"을 구분할 근거가 없고, 후자를 실패로 적으면 실제로 결제한
     * 사용자에게 실패가 보인다.
     */
    @Test
    @DisplayName("완료 호출이 없으면 결제 행은 손대지 않은 채 PENDING 으로 남는다")
    fun abandonedCheckoutLeavesRowUntouched() {
        every { userRepository.findById(userId) } returns
            User(
                id = userId, email = "a@b.c", name = "tester",
                provider = AuthProvider.GOOGLE, providerId = "google-1",
            )
        val saved = slot<Payment>()
        every { paymentRepository.save(capture(saved)) } answers { saved.captured.copy(id = 42) }

        service.createCreditCheckout(userId, CreditPackage.BASIC.name)
        // 사용자가 결제창을 닫았다 — complete 도 웹훅도 오지 않는다.

        assertEquals(PaymentStatus.PENDING, saved.captured.status)
        // 저장은 체크아웃의 1회뿐이고, 상태를 바꾸는 갱신은 없다.
        verify(exactly = 1) { paymentRepository.save(any()) }
        verify(exactly = 0) { paymentRepository.update(any()) }
        verify(exactly = 0) { gateway.getPayment(any()) }
        // 확정되지 않은 결제는 퍼널에 남지 않는다.
        verify(exactly = 0) { eventPublisher.publishEvent(any<PaymentCompletedEvent>()) }
    }

    /**
     * 나중에 웹훅이 도착하면 그때 비로소 COMPLETED 가 된다. 사용자는 이 변화를 다음
     * 조회에서 본다 — 화면이 스스로 갱신되지는 않는다.
     *
     * **실제 웹훅 진입점을 지난다.** `complete` 를 직접 부르면 서명 검증과 웹훅 이력
     * 기록을 건너뛰므로, 브라우저가 사라진 뒤 남는 유일한 완료 경로를 검증했다고 할 수
     * 없다. 그래서 유효 서명·`Transaction.Paid` 본문·이벤트 저장소를 모두 준비하고
     * `handleWebhook` 을 부른다.
     */
    @Test
    @DisplayName("지연된 Transaction.Paid 웹훅이 도착하면 PENDING 이 COMPLETED 로 바뀐다")
    fun delayedWebhookResolvesPending() {
        allowSignature()
        every { webhookEventRepository.saveIfAbsent(any()) } returns true
        every { webhookEventRepository.markProcessed(any(), any()) } returns true

        val pending = Payment(
            id = 42, userId = userId, type = PaymentType.CREDIT,
            amount = CreditPackage.BASIC.price, currency = "KRW",
            status = PaymentStatus.PENDING, pgProvider = "portone",
            description = "CREDIT|BASIC",
        )
        every { paymentRepository.findByIdForUpdate(42) } returns pending
        val updated = slot<Payment>()
        every { paymentRepository.update(capture(updated)) } answers { updated.captured }
        every { gateway.getPayment("ongo-42") } returns PortOnePayment(
            paymentId = "ongo-42", status = "PAID",
            amount = CreditPackage.BASIC.price, currency = "KRW",
            transactionId = "tx-1", paymentMethod = "CARD", receiptUrl = "https://receipt",
        )
        every { creditService.addPurchasedCredits(userId, CreditPackage.BASIC, 42) } returns Unit

        service.handleWebhook(body("Transaction.Paid"), webhookId, signature, timestamp)

        // 서명을 실제로 검증했다.
        verify(exactly = 1) { gateway.verifyWebhookSignature(any(), any(), any(), any()) }
        // PENDING 이 이 웹훅으로 해소됐다.
        assertEquals(PaymentStatus.COMPLETED, updated.captured.status)
        // 크레딧은 정확히 한 번.
        verify(exactly = 1) { creditService.addPurchasedCredits(userId, CreditPackage.BASIC, 42) }
        // 웹훅 이력이 남고 처리 완료로 표시된다 — 재전송이 와도 중복 지급되지 않는 근거다.
        verify(exactly = 1) { webhookEventRepository.saveIfAbsent(any()) }
        verify(exactly = 1) { webhookEventRepository.markProcessed(any(), any()) }
        // 확정된 시점에 퍼널 이벤트가 정확히 한 번 나간다.
        verify(exactly = 1) {
            eventPublisher.publishEvent(
                PaymentCompletedEvent(userId = userId, paymentId = 42, type = PaymentType.CREDIT),
            )
        }
    }
}
