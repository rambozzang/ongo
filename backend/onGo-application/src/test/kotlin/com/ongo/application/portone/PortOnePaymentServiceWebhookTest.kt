package com.ongo.application.portone

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.CreditPackage
import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import com.ongo.common.exception.UnauthorizedException
import com.ongo.domain.payment.Payment
import com.ongo.domain.payment.PaymentRepository
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.UserRepository
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * 포트원 웹훅 처리 테스트.
 *
 * 포트원은 결제 실패/취소 등 모든 이벤트를 같은 엔드포인트로 보내며,
 * 4xx/5xx 응답 시 최대 5회 재전송한다. 따라서 `Transaction.Paid` 외의 이벤트는
 * 예외 없이 조용히 무시해야 재전송 폭풍이 발생하지 않는다.
 */
@ExtendWith(MockKExtension::class)
class PortOnePaymentServiceWebhookTest {

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

    private val webhookId = "webhook-1"
    private val signature = "v1,signature"
    private val timestamp = "1700000000"

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = PortOnePaymentService(
            paymentRepository = paymentRepository,
            subscriptionRepository = subscriptionRepository,
            userRepository = userRepository,
            creditService = creditService,
            gateway = gateway,
            objectMapper = ObjectMapper(),
            storeId = "store-test",
            channelKey = "channel-test",
        )
    }

    private fun body(type: String, paymentId: String = "ongo-42") =
        """{"type":"$type","timestamp":"2026-08-05T10:00:00Z","data":{"paymentId":"$paymentId","storeId":"store-test"}}"""

    private fun allowSignature(valid: Boolean = true) {
        every { gateway.verifyWebhookSignature(any(), any(), any(), any()) } returns valid
    }

    @Test
    @DisplayName("서명이 유효하지 않으면 UnauthorizedException을 던지고 결제를 조회하지 않는다")
    fun invalidSignatureRejected() {
        allowSignature(valid = false)

        assertThrows(UnauthorizedException::class.java) {
            service.handleWebhook(body("Transaction.Paid"), webhookId, signature, timestamp)
        }

        verify(exactly = 0) { gateway.getPayment(any()) }
        verify(exactly = 0) { paymentRepository.findById(any()) }
    }

    @Test
    @DisplayName("Transaction.Paid 웹훅은 결제를 완료 처리한다")
    fun paidWebhookCompletesPayment() {
        allowSignature()
        val payment = Payment(
            id = 42,
            userId = 7,
            type = PaymentType.CREDIT,
            amount = CreditPackage.BASIC.price,
            currency = "KRW",
            status = PaymentStatus.PENDING,
            pgProvider = "portone",
            description = "CREDIT|BASIC",
        )
        every { paymentRepository.findById(42) } returns payment
        every { paymentRepository.update(any()) } returns payment
        every { gateway.getPayment("ongo-42") } returns PortOnePayment(
            paymentId = "ongo-42",
            status = "PAID",
            amount = CreditPackage.BASIC.price,
            currency = "KRW",
            transactionId = "tx-1",
            paymentMethod = "CARD",
            receiptUrl = "https://receipt",
        )
        every { creditService.addPurchasedCredits(7, CreditPackage.BASIC, 42) } just runs

        service.handleWebhook(body("Transaction.Paid"), webhookId, signature, timestamp)

        verify(exactly = 1) { creditService.addPurchasedCredits(7, CreditPackage.BASIC, 42) }
    }

    @Test
    @DisplayName("Transaction.Failed 웹훅은 예외 없이 무시한다 — 재전송 폭풍 방지")
    fun failedWebhookIgnored() {
        allowSignature()

        service.handleWebhook(body("Transaction.Failed"), webhookId, signature, timestamp)

        verify(exactly = 0) { gateway.getPayment(any()) }
        verify(exactly = 0) { paymentRepository.findById(any()) }
    }

    @Test
    @DisplayName("Transaction.Cancelled 웹훅은 예외 없이 무시한다")
    fun cancelledWebhookIgnored() {
        allowSignature()

        service.handleWebhook(body("Transaction.Cancelled"), webhookId, signature, timestamp)

        verify(exactly = 0) { gateway.getPayment(any()) }
    }

    @Test
    @DisplayName("알 수 없는 이벤트 타입도 예외 없이 무시한다")
    fun unknownTypeIgnored() {
        allowSignature()

        service.handleWebhook(body("BillingKey.Issued"), webhookId, signature, timestamp)

        verify(exactly = 0) { gateway.getPayment(any()) }
    }

    @Test
    @DisplayName("Transaction.Paid인데 paymentId가 없으면 예외를 던진다")
    fun paidWithoutPaymentIdThrows() {
        allowSignature()
        val noPaymentId = """{"type":"Transaction.Paid","data":{"storeId":"store-test"}}"""

        assertThrows(IllegalArgumentException::class.java) {
            service.handleWebhook(noPaymentId, webhookId, signature, timestamp)
        }
    }

    @Test
    @DisplayName("본문이 JSON이 아니면 서명 검증 이후 예외를 던진다")
    fun malformedBodyThrows() {
        allowSignature()

        assertThrows(IllegalArgumentException::class.java) {
            service.handleWebhook("not json", webhookId, signature, timestamp)
        }
    }

    @Test
    @DisplayName("서명 검증에는 원문 본문이 그대로 전달된다 — 재직렬화하면 서명이 어긋난다")
    fun rawBodyPassedToVerification() {
        allowSignature()
        val raw = body("Transaction.Failed")

        service.handleWebhook(raw, webhookId, signature, timestamp)

        verify(exactly = 1) { gateway.verifyWebhookSignature(raw, webhookId, signature, timestamp) }
    }
}
