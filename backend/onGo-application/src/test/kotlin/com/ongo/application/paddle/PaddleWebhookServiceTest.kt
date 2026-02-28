package com.ongo.application.paddle

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AuthProvider
import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.common.exception.UnauthorizedException
import com.ongo.domain.payment.Payment
import com.ongo.domain.payment.PaymentRepository
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.User
import com.ongo.domain.user.UserRepository
import com.ongo.domain.webhook.WebhookEvent
import com.ongo.domain.webhook.WebhookEventRepository
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class PaddleWebhookServiceTest {

    @MockK
    private lateinit var paddleGateway: PaddleGateway

    @MockK
    private lateinit var subscriptionRepository: SubscriptionRepository

    @MockK
    private lateinit var paymentRepository: PaymentRepository

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var creditService: CreditService

    @MockK
    private lateinit var webhookEventRepository: WebhookEventRepository

    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    private lateinit var paddleWebhookService: PaddleWebhookService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        paddleWebhookService = PaddleWebhookService(
            paddleGateway,
            subscriptionRepository,
            paymentRepository,
            userRepository,
            creditService,
            objectMapper,
            webhookEventRepository,
        )
    }

    private fun createUser(id: Long, planType: PlanType = PlanType.FREE): User = User(
        id = id,
        email = "user$id@test.com",
        name = "TestUser$id",
        provider = AuthProvider.GOOGLE,
        providerId = "google_$id",
        planType = planType,
    )

    private fun currentTimestamp(): Long = System.currentTimeMillis() / 1000

    private fun createSignature(ts: Long = currentTimestamp(), hash: String = "valid_hash"): String =
        "ts=$ts;h1=$hash"

    private fun buildSubscriptionCreatedPayload(
        eventId: String = "evt_123",
        paddleSubId: String = "sub_paddle_123",
        customerId: String = "ctm_123",
        userId: Long = 1L,
        priceId: String = "pri_starter",
    ): String = """
        {
          "event_type": "subscription.created",
          "event_id": "$eventId",
          "data": {
            "id": "$paddleSubId",
            "customer_id": "$customerId",
            "custom_data": { "user_id": $userId },
            "items": [{ "price": { "id": "$priceId" } }],
            "status": "active",
            "current_billing_period": {
              "starts_at": "2024-01-01T00:00:00Z",
              "ends_at": "2024-02-01T00:00:00Z"
            }
          }
        }
    """.trimIndent()

    private fun buildSubscriptionCanceledPayload(
        eventId: String = "evt_cancel_1",
        paddleSubId: String = "sub_paddle_123",
    ): String = """
        {
          "event_type": "subscription.canceled",
          "event_id": "$eventId",
          "data": {
            "id": "$paddleSubId",
            "customer_id": "ctm_123",
            "status": "canceled"
          }
        }
    """.trimIndent()

    private fun buildTransactionCompletedPayload(
        eventId: String = "evt_txn_1",
        transactionId: String = "txn_123",
        userId: Long = 1L,
        subscriptionId: String? = "sub_paddle_123",
        totalAmount: String = "9900",
        currencyCode: String = "KRW",
    ): String {
        val subscriptionField = if (subscriptionId != null) {
            """"subscription_id": "$subscriptionId","""
        } else {
            ""
        }
        return """
            {
              "event_type": "transaction.completed",
              "event_id": "$eventId",
              "data": {
                "id": "$transactionId",
                "custom_data": { "user_id": $userId },
                $subscriptionField
                "details": {
                  "totals": {
                    "total": "$totalAmount",
                    "currency_code": "$currencyCode"
                  }
                },
                "payments": [
                  {
                    "method_details": {
                      "type": "card"
                    }
                  }
                ]
              }
            }
        """.trimIndent()
    }

    private fun buildTransactionRefundedPayload(
        eventId: String = "evt_refund_1",
        transactionId: String = "txn_123",
    ): String = """
        {
          "event_type": "transaction.refunded",
          "event_id": "$eventId",
          "data": {
            "id": "$transactionId"
          }
        }
    """.trimIndent()

    @Test
    @DisplayName("서명 검증 실패 시 UnauthorizedException이 발생해야 한다")
    fun `서명 검증 실패 - UnauthorizedException`() {
        // given
        val rawBody = buildSubscriptionCreatedPayload()
        val signature = createSignature()

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns false

        // when & then
        assertThrows(UnauthorizedException::class.java) {
            paddleWebhookService.handleWebhook(rawBody, signature)
        }
    }

    @Test
    @DisplayName("이미 처리된 이벤트는 스킵되어야 한다")
    fun `이미 처리된 이벤트 스킵`() {
        // given
        val rawBody = buildSubscriptionCreatedPayload(eventId = "evt_already_processed")
        val signature = createSignature()

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true
        every { webhookEventRepository.findByEventId("evt_already_processed") } returns WebhookEvent(
            id = 1L,
            eventId = "evt_already_processed",
            eventType = "subscription.created",
            payload = rawBody,
            status = "PROCESSED",
        )

        // when
        paddleWebhookService.handleWebhook(rawBody, signature)

        // then - 구독 처리가 일어나지 않아야 함
        verify(exactly = 0) { subscriptionRepository.save(any()) }
        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }

    @Test
    @DisplayName("subscription.created 이벤트 시 구독이 생성/업데이트되어야 한다 (기존 구독 없는 경우)")
    fun `subscription created - 신규 구독 생성`() {
        // given
        val rawBody = buildSubscriptionCreatedPayload(
            eventId = "evt_new_sub",
            paddleSubId = "sub_new_123",
            customerId = "ctm_new",
            userId = 10L,
            priceId = "pri_starter",
        )
        val signature = createSignature()
        val user = createUser(10L)

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true
        every { webhookEventRepository.findByEventId("evt_new_sub") } returns null
        every { webhookEventRepository.save(any()) } answers {
            firstArg<WebhookEvent>().copy(id = 1L)
        }
        every { webhookEventRepository.update(any()) } returns Unit
        every { subscriptionRepository.findByUserId(10L) } returns null
        every { subscriptionRepository.save(any()) } answers { firstArg() }
        every { userRepository.findById(10L) } returns user
        every { userRepository.update(any()) } answers { firstArg() }
        every { paddleGateway.getPriceIdForPlan("STARTER") } returns "pri_starter"
        every { paddleGateway.getPriceIdForPlan("PRO") } returns "pri_pro"
        every { paddleGateway.getPriceIdForPlan("BUSINESS") } returns "pri_business"

        // when
        paddleWebhookService.handleWebhook(rawBody, signature)

        // then
        val subSlot = slot<Subscription>()
        verify { subscriptionRepository.save(capture(subSlot)) }
        assertEquals(10L, subSlot.captured.userId)
        assertEquals(PlanType.STARTER, subSlot.captured.planType)
        assertEquals(SubscriptionStatus.ACTIVE, subSlot.captured.status)
        assertEquals("sub_new_123", subSlot.captured.paddleSubscriptionId)
        assertEquals("ctm_new", subSlot.captured.paddleCustomerId)

        val userSlot = slot<User>()
        verify { userRepository.update(capture(userSlot)) }
        assertEquals(PlanType.STARTER, userSlot.captured.planType)
        assertEquals("ctm_new", userSlot.captured.paddleCustomerId)
    }

    @Test
    @DisplayName("subscription.created 이벤트 시 기존 구독이 있으면 업데이트되어야 한다")
    fun `subscription created - 기존 구독 업데이트`() {
        // given
        val rawBody = buildSubscriptionCreatedPayload(
            eventId = "evt_update_sub",
            paddleSubId = "sub_update_123",
            customerId = "ctm_update",
            userId = 20L,
            priceId = "pri_pro",
        )
        val signature = createSignature()
        val existingSub = Subscription(
            id = 5L,
            userId = 20L,
            planType = PlanType.FREE,
            status = SubscriptionStatus.FREE,
        )
        val user = createUser(20L)

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true
        every { webhookEventRepository.findByEventId("evt_update_sub") } returns null
        every { webhookEventRepository.save(any()) } answers {
            firstArg<WebhookEvent>().copy(id = 2L)
        }
        every { webhookEventRepository.update(any()) } returns Unit
        every { subscriptionRepository.findByUserId(20L) } returns existingSub
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { userRepository.findById(20L) } returns user
        every { userRepository.update(any()) } answers { firstArg() }
        every { paddleGateway.getPriceIdForPlan("STARTER") } returns "pri_starter"
        every { paddleGateway.getPriceIdForPlan("PRO") } returns "pri_pro"
        every { paddleGateway.getPriceIdForPlan("BUSINESS") } returns "pri_business"

        // when
        paddleWebhookService.handleWebhook(rawBody, signature)

        // then
        val subSlot = slot<Subscription>()
        verify { subscriptionRepository.update(capture(subSlot)) }
        assertEquals(20L, subSlot.captured.userId)
        assertEquals(PlanType.PRO, subSlot.captured.planType)
        assertEquals(SubscriptionStatus.ACTIVE, subSlot.captured.status)
        assertEquals("sub_update_123", subSlot.captured.paddleSubscriptionId)
    }

    @Test
    @DisplayName("subscription.canceled 이벤트 시 구독 상태가 CANCELLED로 변경되어야 한다")
    fun `subscription canceled - 상태 변경`() {
        // given
        val rawBody = buildSubscriptionCanceledPayload(
            eventId = "evt_cancel_test",
            paddleSubId = "sub_cancel_123",
        )
        val signature = createSignature()
        val existingSub = Subscription(
            id = 10L,
            userId = 30L,
            planType = PlanType.STARTER,
            status = SubscriptionStatus.ACTIVE,
            paddleSubscriptionId = "sub_cancel_123",
        )

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true
        every { webhookEventRepository.findByEventId("evt_cancel_test") } returns null
        every { webhookEventRepository.save(any()) } answers {
            firstArg<WebhookEvent>().copy(id = 3L)
        }
        every { webhookEventRepository.update(any()) } returns Unit
        every { subscriptionRepository.findByPaddleSubscriptionId("sub_cancel_123") } returns existingSub
        every { subscriptionRepository.update(any()) } answers { firstArg() }

        // when
        paddleWebhookService.handleWebhook(rawBody, signature)

        // then
        val subSlot = slot<Subscription>()
        verify { subscriptionRepository.update(capture(subSlot)) }
        assertEquals(SubscriptionStatus.CANCELLED, subSlot.captured.status)
    }

    @Test
    @DisplayName("transaction.completed 이벤트 시 Payment가 생성되어야 한다 (구독 결제)")
    fun `transaction completed - 구독 결제 Payment 생성`() {
        // given
        val rawBody = buildTransactionCompletedPayload(
            eventId = "evt_txn_sub",
            transactionId = "txn_sub_123",
            userId = 40L,
            subscriptionId = "sub_paddle_40",
            totalAmount = "19900",
            currencyCode = "KRW",
        )
        val signature = createSignature()

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true
        every { webhookEventRepository.findByEventId("evt_txn_sub") } returns null
        every { webhookEventRepository.save(any()) } answers {
            firstArg<WebhookEvent>().copy(id = 4L)
        }
        every { webhookEventRepository.update(any()) } returns Unit
        every { paymentRepository.findByPaddleTransactionId("txn_sub_123") } returns null
        every { paddleGateway.getTransactionInvoice("txn_sub_123") } returns "https://paddle.com/invoice/123"
        every { paymentRepository.save(any()) } answers {
            firstArg<Payment>().copy(id = 100L)
        }

        // when
        paddleWebhookService.handleWebhook(rawBody, signature)

        // then
        val paymentSlot = slot<Payment>()
        verify { paymentRepository.save(capture(paymentSlot)) }
        assertEquals(40L, paymentSlot.captured.userId)
        assertEquals(PaymentType.SUBSCRIPTION, paymentSlot.captured.type)
        assertEquals(19900, paymentSlot.captured.amount)
        assertEquals("KRW", paymentSlot.captured.currency)
        assertEquals(PaymentStatus.COMPLETED, paymentSlot.captured.status)
        assertEquals("paddle", paymentSlot.captured.pgProvider)
        assertEquals("txn_sub_123", paymentSlot.captured.paddleTransactionId)
        assertEquals("https://paddle.com/invoice/123", paymentSlot.captured.paddleInvoiceUrl)

        // 구독 결제이므로 크레딧 추가 안 됨
        verify(exactly = 0) { creditService.addPurchasedCredits(any(), any(), any()) }
    }

    @Test
    @DisplayName("transaction.completed 이벤트 시 CREDIT 유형이면 크레딧이 추가되어야 한다")
    fun `transaction completed - 크레딧 구매 시 크레딧 추가`() {
        // given
        val rawBody = buildTransactionCompletedPayload(
            eventId = "evt_txn_credit",
            transactionId = "txn_credit_123",
            userId = 50L,
            subscriptionId = null, // subscription_id 없음 → CREDIT 유형
            totalAmount = "5000",
            currencyCode = "KRW",
        )
        val signature = createSignature()

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true
        every { webhookEventRepository.findByEventId("evt_txn_credit") } returns null
        every { webhookEventRepository.save(any()) } answers {
            firstArg<WebhookEvent>().copy(id = 5L)
        }
        every { webhookEventRepository.update(any()) } returns Unit
        every { paymentRepository.findByPaddleTransactionId("txn_credit_123") } returns null
        every { paddleGateway.getTransactionInvoice("txn_credit_123") } returns "https://paddle.com/invoice/credit"
        every { paymentRepository.save(any()) } answers {
            firstArg<Payment>().copy(id = 200L)
        }
        every { creditService.addPurchasedCredits(50L, 5000, 200L) } returns Unit

        // when
        paddleWebhookService.handleWebhook(rawBody, signature)

        // then
        val paymentSlot = slot<Payment>()
        verify { paymentRepository.save(capture(paymentSlot)) }
        assertEquals(PaymentType.CREDIT, paymentSlot.captured.type)
        assertEquals("AI 크레딧 구매", paymentSlot.captured.description)

        verify { creditService.addPurchasedCredits(50L, 5000, 200L) }
    }

    @Test
    @DisplayName("transaction.refunded 이벤트 시 환불 처리 및 CREDIT 유형이면 크레딧이 회수되어야 한다")
    fun `transaction refunded - 환불 및 크레딧 회수`() {
        // given
        val rawBody = buildTransactionRefundedPayload(
            eventId = "evt_refund_test",
            transactionId = "txn_refund_123",
        )
        val signature = createSignature()
        val existingPayment = Payment(
            id = 300L,
            userId = 60L,
            type = PaymentType.CREDIT,
            amount = 3000,
            status = PaymentStatus.COMPLETED,
            paddleTransactionId = "txn_refund_123",
        )

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true
        every { webhookEventRepository.findByEventId("evt_refund_test") } returns null
        every { webhookEventRepository.save(any()) } answers {
            firstArg<WebhookEvent>().copy(id = 6L)
        }
        every { webhookEventRepository.update(any()) } returns Unit
        every { paymentRepository.findByPaddleTransactionId("txn_refund_123") } returns existingPayment
        every { paymentRepository.update(any()) } answers { firstArg() }
        every { creditService.revokeCredits(60L, 3000, "REFUND_txn_refund_123") } returns Unit

        // when
        paddleWebhookService.handleWebhook(rawBody, signature)

        // then
        val paymentSlot = slot<Payment>()
        verify { paymentRepository.update(capture(paymentSlot)) }
        assertEquals(PaymentStatus.REFUNDED, paymentSlot.captured.status)

        verify { creditService.revokeCredits(60L, 3000, "REFUND_txn_refund_123") }
    }

    @Test
    @DisplayName("transaction.refunded 이벤트 시 SUBSCRIPTION 유형이면 크레딧 회수가 일어나지 않아야 한다")
    fun `transaction refunded - 구독 환불은 크레딧 회수 안 함`() {
        // given
        val rawBody = buildTransactionRefundedPayload(
            eventId = "evt_refund_sub",
            transactionId = "txn_refund_sub_123",
        )
        val signature = createSignature()
        val existingPayment = Payment(
            id = 400L,
            userId = 70L,
            type = PaymentType.SUBSCRIPTION,
            amount = 9900,
            status = PaymentStatus.COMPLETED,
            paddleTransactionId = "txn_refund_sub_123",
        )

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true
        every { webhookEventRepository.findByEventId("evt_refund_sub") } returns null
        every { webhookEventRepository.save(any()) } answers {
            firstArg<WebhookEvent>().copy(id = 7L)
        }
        every { webhookEventRepository.update(any()) } returns Unit
        every { paymentRepository.findByPaddleTransactionId("txn_refund_sub_123") } returns existingPayment
        every { paymentRepository.update(any()) } answers { firstArg() }

        // when
        paddleWebhookService.handleWebhook(rawBody, signature)

        // then
        verify { paymentRepository.update(any()) }
        verify(exactly = 0) { creditService.revokeCredits(any(), any(), any()) }
    }

    @Test
    @DisplayName("타임스탬프가 5분 이상 지난 경우 UnauthorizedException이 발생해야 한다")
    fun `타임스탬프 만료 - UnauthorizedException`() {
        // given
        val rawBody = buildSubscriptionCreatedPayload()
        val expiredTimestamp = currentTimestamp() - 600 // 10분 전
        val signature = createSignature(ts = expiredTimestamp)

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true

        // when & then
        assertThrows(UnauthorizedException::class.java) {
            paddleWebhookService.handleWebhook(rawBody, signature)
        }
    }

    @Test
    @DisplayName("타임스탬프가 5분 이내이면 정상 처리되어야 한다")
    fun `타임스탬프 유효 - 정상 처리`() {
        // given
        val rawBody = buildSubscriptionCreatedPayload(
            eventId = "evt_valid_ts",
            userId = 80L,
            priceId = "pri_starter",
        )
        val validTimestamp = currentTimestamp() - 60 // 1분 전
        val signature = createSignature(ts = validTimestamp)
        val user = createUser(80L)

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true
        every { webhookEventRepository.findByEventId("evt_valid_ts") } returns null
        every { webhookEventRepository.save(any()) } answers {
            firstArg<WebhookEvent>().copy(id = 8L)
        }
        every { webhookEventRepository.update(any()) } returns Unit
        every { subscriptionRepository.findByUserId(80L) } returns null
        every { subscriptionRepository.save(any()) } answers { firstArg() }
        every { userRepository.findById(80L) } returns user
        every { userRepository.update(any()) } answers { firstArg() }
        every { paddleGateway.getPriceIdForPlan("STARTER") } returns "pri_starter"
        every { paddleGateway.getPriceIdForPlan("PRO") } returns "pri_pro"
        every { paddleGateway.getPriceIdForPlan("BUSINESS") } returns "pri_business"

        // when - 예외 없이 정상 처리
        paddleWebhookService.handleWebhook(rawBody, signature)

        // then
        verify { subscriptionRepository.save(any()) }
    }

    @Test
    @DisplayName("중복 트랜잭션이 감지되면 Payment를 생성하지 않아야 한다")
    fun `중복 트랜잭션 스킵`() {
        // given
        val rawBody = buildTransactionCompletedPayload(
            eventId = "evt_dup_txn",
            transactionId = "txn_dup_123",
            userId = 90L,
        )
        val signature = createSignature()
        val existingPayment = Payment(
            id = 500L,
            userId = 90L,
            type = PaymentType.SUBSCRIPTION,
            amount = 9900,
            status = PaymentStatus.COMPLETED,
            paddleTransactionId = "txn_dup_123",
        )

        every { paddleGateway.verifyWebhookSignature(rawBody, signature) } returns true
        every { webhookEventRepository.findByEventId("evt_dup_txn") } returns null
        every { webhookEventRepository.save(any()) } answers {
            firstArg<WebhookEvent>().copy(id = 9L)
        }
        every { webhookEventRepository.update(any()) } returns Unit
        every { paymentRepository.findByPaddleTransactionId("txn_dup_123") } returns existingPayment

        // when
        paddleWebhookService.handleWebhook(rawBody, signature)

        // then - save가 호출되지 않아야 함 (이미 처리된 트랜잭션)
        verify(exactly = 0) { paymentRepository.save(any()) }
    }
}
