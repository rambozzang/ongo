package com.ongo.application.portone

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.CreditPackage
import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import com.ongo.common.enums.PlanType
import com.ongo.common.exception.UnauthorizedException
import com.ongo.domain.payment.Payment
import com.ongo.application.subscription.DummyTransactionManagerForTest
import com.ongo.application.webhook.WebhookEventRecorder
import com.ongo.application.webhook.WebhookInboundGuard
import com.ongo.domain.payment.PaymentRepository
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.UserRepository
import com.ongo.application.payment.PaymentCompletedEvent
import com.ongo.domain.webhook.WebhookEvent
import com.ongo.domain.webhook.WebhookEventRepository
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher

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

    @MockK
    private lateinit var webhookEventRepository: WebhookEventRepository

    /** 확정 결제 퍼널 이벤트 통로. 중복 웹훅이 추가 발행을 만들지 않는지 여기서 본다. */
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private lateinit var service: PortOnePaymentService

    /** `webhook_events` 대신 쓰는 인메모리 저장소. 수신 기록 후 재조회를 재현한다. */
    private val storedEvents = mutableMapOf<String, WebhookEvent>()

    private val webhookId = "webhook-1"
    private val signature = "v1,signature"
    private val timestamp = "1700000000"

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        storedEvents.clear()
        /*
         * 수신 기록은 이제 원자적 삽입 후 그 행을 **다시 읽어** 쓴다. 그 왕복을 재현하려면
         * 저장소가 상태를 가져야 하므로 작은 인메모리 가짜를 둔다. 중복 케이스는
         * [storedEvents] 에 PROCESSED 행을 미리 넣어 만든다.
         */
        every { webhookEventRepository.saveIfAbsent(any()) } answers {
            val event = firstArg<WebhookEvent>()
            storedEvents.putIfAbsent(event.eventId, event.copy(id = 1L)) == null
        }
        every { webhookEventRepository.findByEventId(any()) } answers { storedEvents[firstArg()] }
        // 잠금 조회도 같은 행을 돌려준다. 경합 자체는 별도 테스트에서 본다.
        every { webhookEventRepository.findByEventIdForUpdate(any()) } answers { storedEvents[firstArg()] }
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
            // 웹훅 처리는 준비 검사를 지나지 않지만 생성자에 필요하다.
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

    private fun body(type: String, paymentId: String = "ongo-42") =
        """{"type":"$type","timestamp":"2026-08-05T10:00:00Z","data":{"paymentId":"$paymentId","storeId":"store-test"}}"""

    /**
     * 결제가 정상 완료되는 최소 구성.
     *
     * 멱등 키 모양만 보는 테스트에도 이것이 필요하다. 예전에는 `saveIfAbsent` 가 false 를
     * 돌려주면 업무 처리 전에 반환됐지만, 이제 멱등 판정은 "처리가 끝났는가"이므로 키를
     * 기록한 뒤 실제로 처리까지 진행한다. 예외를 삼켜 넘기면 그 경로를 검증하지 않는 셈이라,
     * **성공하는 경로를 갖춰 두고** 키를 확인한다.
     */
    private fun allowPaidProcessing(portonePaymentId: String = "ongo-42") {
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
        every { paymentRepository.findByIdForUpdate(42) } returns payment
        every { paymentRepository.update(any()) } returns payment
        every { gateway.getPayment(portonePaymentId) } returns PortOnePayment(
            paymentId = portonePaymentId,
            status = "PAID",
            amount = CreditPackage.BASIC.price,
            currency = "KRW",
            transactionId = "tx-1",
            paymentMethod = "CARD",
            receiptUrl = "https://receipt",
        )
        every { creditService.addPurchasedCredits(7, CreditPackage.BASIC, 42) } just runs
    }

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
        verify(exactly = 0) { paymentRepository.findByIdForUpdate(any()) }
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
        every { paymentRepository.findByIdForUpdate(42) } returns payment
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

    /**
     * 지급은 `description` 을 `CREDIT|<패키지>` 로 파싱해 패키지를 정한다. 폐기된 레거시
     * 경로(`POST /credits/purchase`)는 `스타터 팩 (500 크레딧)` 형식을 썼다.
     *
     * 그 형식의 결제 행이 어떤 경로로든 다시 생겨 PG 검증까지 통과하더라도, 패키지를
     * 정할 수 없으므로 **지급이 일어나서는 안 된다.** 임의 패키지로 추정해 지급하면
     * 결제 금액과 무관한 크레딧이 나간다.
     */
    @Test
    @DisplayName("CREDIT| 형식이 아닌 결제는 검증을 통과해도 크레딧을 지급하지 않는다")
    fun legacyDescriptionNeverGrants() {
        allowSignature()
        val payment = Payment(
            id = 42,
            userId = 7,
            type = PaymentType.CREDIT,
            amount = CreditPackage.STARTER.price,
            currency = "KRW",
            status = PaymentStatus.PENDING,
            pgProvider = "portone",
            // 폐기된 CreditPurchaseUseCase 가 쓰던 형식. 구분자가 없다.
            description = "스타터 팩 (500 크레딧)",
        )
        every { paymentRepository.findByIdForUpdate(42) } returns payment
        every { paymentRepository.update(any()) } returns payment
        every { gateway.getPayment("ongo-42") } returns PortOnePayment(
            paymentId = "ongo-42",
            status = "PAID",
            amount = CreditPackage.STARTER.price,
            currency = "KRW",
            transactionId = "tx-1",
            paymentMethod = "CARD",
            receiptUrl = "https://receipt",
        )

        assertThrows(IllegalStateException::class.java) {
            service.handleWebhook(body("Transaction.Paid"), webhookId, signature, timestamp)
        }

        verify(exactly = 0) { creditService.addPurchasedCredits(any(), any(), any()) }
    }

    /**
     * 구독 결제 직후 크레딧이 FREE 기준(30)에 머물면 STARTER 를 산 사용자가 쇼츠 실행
     * 한 번(37)도 못 돌린다. 구독만 ACTIVE 로 바뀌고 쓸 수 있는 것은 그대로인 상태다.
     */
    @Test
    @DisplayName("구독 결제 완료는 플랜 크레딧 권한을 적용한다")
    fun subscriptionPaidAppliesPlanEntitlement() {
        allowSignature()
        val payment = Payment(
            id = 43,
            userId = 7,
            type = PaymentType.SUBSCRIPTION,
            amount = PlanType.STARTER.price,
            currency = "KRW",
            status = PaymentStatus.PENDING,
            pgProvider = "portone",
            description = "SUBSCRIPTION|STARTER|MONTHLY",
        )
        every { paymentRepository.findByIdForUpdate(43) } returns payment
        every { paymentRepository.update(any()) } returns payment
        every { gateway.getPayment("ongo-43") } returns PortOnePayment(
            paymentId = "ongo-43",
            status = "PAID",
            amount = PlanType.STARTER.price,
            currency = "KRW",
            transactionId = "tx-2",
            paymentMethod = "CARD",
            receiptUrl = "https://receipt",
        )
        every { subscriptionRepository.findByUserId(7) } returns mockk(relaxed = true)
        every { subscriptionRepository.update(any()) } answers { firstArg() }
        every { userRepository.findById(7) } returns mockk(relaxed = true)
        every { userRepository.update(any()) } answers { firstArg() }
        every { creditService.applyPlanEntitlement(any(), any(), any()) } just runs

        service.handleWebhook(body("Transaction.Paid", paymentId = "ongo-43"), webhookId, signature, timestamp)

        verify(exactly = 1) {
            creditService.applyPlanEntitlement(7, PlanType.STARTER, "SUBSCRIPTION_PAID")
        }
        // 구독 결제는 구매 크레딧 팩과 무관하다.
        verify(exactly = 0) { creditService.addPurchasedCredits(any(), any(), any()) }
    }

    @Test
    @DisplayName("Paid 웹훅은 잠금 조회로 결제를 읽는다 — 중복 크레딧 지급 방지의 근거")
    fun paidWebhookUsesLockingRead() {
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
        every { paymentRepository.findByIdForUpdate(42) } returns payment
        every { paymentRepository.update(any()) } returns payment
        every { gateway.getPayment("ongo-42") } returns PortOnePayment(
            paymentId = "ongo-42",
            status = "PAID",
            amount = CreditPackage.BASIC.price,
            currency = "KRW",
            transactionId = "tx-1",
            paymentMethod = "CARD",
            receiptUrl = null,
        )
        every { creditService.addPurchasedCredits(any(), any(), any()) } just runs

        service.handleWebhook(body("Transaction.Paid"), webhookId, signature, timestamp)

        verify(exactly = 1) { paymentRepository.findByIdForUpdate(42) }
        verify(exactly = 0) { paymentRepository.findById(any()) }
    }

    @Test
    @DisplayName("이미 COMPLETED인 결제에 Paid 웹훅이 다시 오면 크레딧을 재지급하지 않는다")
    fun duplicatePaidWebhookDoesNotGrantTwice() {
        allowSignature()
        val completed = Payment(
            id = 42,
            userId = 7,
            type = PaymentType.CREDIT,
            amount = CreditPackage.BASIC.price,
            currency = "KRW",
            status = PaymentStatus.COMPLETED,
            pgProvider = "portone",
            description = "CREDIT|BASIC",
        )
        every { paymentRepository.findByIdForUpdate(42) } returns completed

        service.handleWebhook(body("Transaction.Paid"), webhookId, signature, timestamp)

        verify(exactly = 0) { creditService.addPurchasedCredits(any(), any(), any()) }
        verify(exactly = 0) { paymentRepository.update(any()) }
        // 잠금 조회로 이미 COMPLETED를 확인했으므로 포트원 재조회조차 필요 없다
        verify(exactly = 0) { gateway.getPayment(any()) }
        /*
         * 퍼널 로그도 늘지 않는다. 포트원은 2xx가 아니면 최대 5회 재전송하므로 중복 웹훅은
         * 예외가 아니라 정상 상황이다. 여기서 이벤트가 한 번 더 나가면 활동 로그에 결제
         * 한 건이 여러 번 남아 "실제로 결제한 사람 수"가 부풀려진다.
         */
        verify(exactly = 0) { eventPublisher.publishEvent(any<PaymentCompletedEvent>()) }
    }

    @Test
    @DisplayName("Transaction.Failed 웹훅은 예외 없이 무시한다 — 재전송 폭풍 방지")
    fun failedWebhookIgnored() {
        allowSignature()

        service.handleWebhook(body("Transaction.Failed"), webhookId, signature, timestamp)

        verify(exactly = 0) { gateway.getPayment(any()) }
        verify(exactly = 0) { paymentRepository.findByIdForUpdate(any()) }
    }

    @Test
    @DisplayName("처리에 성공하면 이력을 PROCESSED로 남긴다 — PENDING으로 방치하면 미처리 인덱스가 쌓인다")
    fun successMarksWebhookEventProcessed() {
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
        every { paymentRepository.findByIdForUpdate(42) } returns payment
        every { paymentRepository.update(any()) } returns payment
        every { gateway.getPayment("ongo-42") } returns PortOnePayment(
            paymentId = "ongo-42",
            status = "PAID",
            amount = CreditPackage.BASIC.price,
            currency = "KRW",
            transactionId = "tx-1",
            paymentMethod = "CARD",
            receiptUrl = null,
        )
        every { creditService.addPurchasedCredits(any(), any(), any()) } just runs

        service.handleWebhook(body("Transaction.Paid"), webhookId, signature, timestamp)

        verify(exactly = 1) { webhookEventRepository.markProcessed("portone:$webhookId", any()) }
    }

    @Test
    @DisplayName("이력 갱신이 0행이어도 결제 처리를 되돌리지 않는다 — 이력 한 줄보다 결제 반영이 우선")
    fun failedHistoryUpdateDoesNotBreakPayment() {
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
        every { paymentRepository.findByIdForUpdate(42) } returns payment
        every { paymentRepository.update(any()) } returns payment
        every { gateway.getPayment("ongo-42") } returns PortOnePayment(
            paymentId = "ongo-42",
            status = "PAID",
            amount = CreditPackage.BASIC.price,
            currency = "KRW",
            transactionId = "tx-1",
            paymentMethod = "CARD",
            receiptUrl = null,
        )
        every { creditService.addPurchasedCredits(any(), any(), any()) } just runs
        // 같은 트랜잭션에서 방금 삽입한 행이라 정상 DB에서는 일어나지 않는다.
        // 일어난다면 키 불일치 같은 프로그래밍 오류이고, 그때 예외를 던지면 모든 웹훅이
        // 같은 지점에서 죽어 결제·취소 반영이 영구 실패한다. error 로그만 남기고 진행한다.
        every { webhookEventRepository.markProcessed(any(), any()) } returns false

        service.handleWebhook(body("Transaction.Paid"), webhookId, signature, timestamp)

        verify(exactly = 1) { creditService.addPurchasedCredits(7, CreditPackage.BASIC, 42) }
    }

    @Test
    @DisplayName("처리에 실패하면 PROCESSED로 남기지 않는다 — 트랜잭션이 롤백돼 재전송으로 복구된다")
    fun failureDoesNotMarkProcessed() {
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
        every { paymentRepository.findByIdForUpdate(42) } returns payment
        every { gateway.getPayment("ongo-42") } returns PortOnePayment(
            paymentId = "ongo-42",
            status = "READY",
            amount = CreditPackage.BASIC.price,
            currency = "KRW",
            transactionId = null,
            paymentMethod = null,
            receiptUrl = null,
        )

        assertThrows(IllegalArgumentException::class.java) {
            service.handleWebhook(body("Transaction.Paid"), webhookId, signature, timestamp)
        }

        verify(exactly = 0) { webhookEventRepository.markProcessed(any(), any()) }
    }

    @Test
    @DisplayName("이미 처리한 webhook-id면 처리하지 않고 조용히 반환한다 — 재전송 멱등")
    fun duplicateWebhookIdIsIgnored() {
        allowSignature()
        // 멱등 판정 기준은 "행이 있다"가 아니라 "처리가 끝났다"이다. 실패한 이력은 재시도돼야 한다.
        storedEvents["portone:$webhookId"] = WebhookEvent(
            id = 1L,
            eventId = "portone:$webhookId",
            eventType = "Transaction.Paid",
            payload = "{}",
            status = "PROCESSED",
        )

        service.handleWebhook(body("Transaction.Paid"), webhookId, signature, timestamp)

        // 컨트롤러가 200을 돌려주도록 예외 없이 끝나야 한다
        verify(exactly = 0) { gateway.getPayment(any()) }
        verify(exactly = 0) { paymentRepository.findByIdForUpdate(any()) }
        verify(exactly = 0) { creditService.addPurchasedCredits(any(), any(), any()) }
        // 이미 처리된 이력을 다시 건드리지 않는다
        verify(exactly = 0) { webhookEventRepository.markProcessed(any(), any()) }
    }

    @Test
    @DisplayName("멱등 키는 portone: 접두사를 붙여 Paddle 이벤트 ID와 충돌하지 않게 한다")
    fun idempotencyKeyIsPrefixed() {
        allowSignature()
        allowPaidProcessing()

        service.handleWebhook(body("Transaction.Paid"), webhookId, signature, timestamp)

        val saved = storedEvents.values.single()
        assertEquals("portone:$webhookId", saved.eventId)
        assertEquals("Transaction.Paid", saved.eventType)
    }

    @Test
    @DisplayName("webhook-id가 지나치게 길면 잘라내지 않고 해시로 대체한다 — event_id는 VARCHAR(200)")
    fun overlongWebhookIdIsHashedNotTruncated() {
        allowSignature()
        val longId = "w".repeat(500)
        allowPaidProcessing()

        service.handleWebhook(body("Transaction.Paid"), longId, signature, timestamp)

        val key = storedEvents.values.single().eventId
        assertTrue(key.length <= 200, "event_id 컬럼 길이를 넘으면 삽입이 실패한다: ${key.length}")
        assertTrue(key.startsWith("portone:sha256:"), "해시 폴백이어야 한다: $key")
        // 자르기였다면 서로 다른 웹훅이 같은 키가 되어 정상 웹훅을 삼킨다
        assertFalse(key == "portone:" + "w".repeat(192), "잘라내면 안 된다")
    }

    @Test
    @DisplayName("같은 긴 webhook-id는 항상 같은 해시 키를 만든다 — 멱등이 유지돼야 한다")
    fun overlongWebhookIdHashIsDeterministic() {
        allowSignature()
        val longId = "z".repeat(300)
        val keys = mutableListOf<String>()
        every { webhookEventRepository.saveIfAbsent(any()) } answers {
            val event = firstArg<WebhookEvent>()
            keys += event.eventId
            storedEvents.putIfAbsent(event.eventId, event.copy(id = 1L)) == null
        }

        allowPaidProcessing()

        service.handleWebhook(body("Transaction.Paid"), longId, signature, timestamp)
        service.handleWebhook(body("Transaction.Paid"), longId, signature, timestamp)

        // 두 번째는 이미 있는 행을 그대로 쓰므로 삽입 시도는 한 번만 일어난다.
        assertEquals(1, keys.size)
        assertEquals(1, storedEvents.size, "같은 긴 id 가 서로 다른 키를 만들었다: ${storedEvents.keys}")
    }

    @Test
    @DisplayName("처리 대상이 아닌 이벤트는 멱등 게이트도 타지 않는다 — 불필요한 행을 남기지 않는다")
    fun ignoredEventDoesNotTouchWebhookEvents() {
        allowSignature()

        service.handleWebhook(body("BillingKey.Issued"), webhookId, signature, timestamp)

        verify(exactly = 0) { webhookEventRepository.saveIfAbsent(any()) }
    }

    @Test
    @DisplayName("가상계좌 발급 등 처리 대상이 아닌 결제 이벤트도 예외 없이 무시한다")
    fun virtualAccountIssuedIgnored() {
        allowSignature()

        service.handleWebhook(body("Transaction.VirtualAccountIssued"), webhookId, signature, timestamp)

        verify(exactly = 0) { gateway.getPayment(any()) }
        verify(exactly = 0) { paymentRepository.findByIdForUpdate(any()) }
    }

    @Test
    @DisplayName("알 수 없는 이벤트 타입도 예외 없이 무시한다")
    fun unknownTypeIgnored() {
        allowSignature()

        service.handleWebhook(body("BillingKey.Issued"), webhookId, signature, timestamp)

        verify(exactly = 0) { gateway.getPayment(any()) }
    }

    @Test
    @DisplayName("Transaction.Paid인데 paymentId가 없으면 영구 오류 예외를 던진다 — 400으로 분류돼야 한다")
    fun paidWithoutPaymentIdThrows() {
        allowSignature()
        val noPaymentId = """{"type":"Transaction.Paid","data":{"storeId":"store-test"}}"""

        assertThrows(PortOneWebhookFormatException::class.java) {
            service.handleWebhook(noPaymentId, webhookId, signature, timestamp)
        }
    }

    @Test
    @DisplayName("본문이 JSON이 아니면 영구 오류 예외를 던진다 — 400으로 분류돼야 한다")
    fun malformedBodyThrows() {
        allowSignature()

        assertThrows(PortOneWebhookFormatException::class.java) {
            service.handleWebhook("not json", webhookId, signature, timestamp)
        }
    }

    @Test
    @DisplayName("결제 상태가 아직 PAID가 아니면 영구 오류가 아닌 일반 예외를 던진다 — 500으로 분류돼 재전송돼야 한다")
    fun notYetPaidThrowsRetryableError() {
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
        every { paymentRepository.findByIdForUpdate(42) } returns payment
        every { gateway.getPayment("ongo-42") } returns PortOnePayment(
            paymentId = "ongo-42",
            status = "READY",
            amount = CreditPackage.BASIC.price,
            currency = "KRW",
            transactionId = null,
            paymentMethod = null,
            receiptUrl = null,
        )

        val thrown = assertThrows(IllegalArgumentException::class.java) {
            service.handleWebhook(body("Transaction.Paid"), webhookId, signature, timestamp)
        }
        // PortOneWebhookFormatException이면 컨트롤러가 400으로 끊어 재전송이 막힌다
        assertFalse(
            thrown is PortOneWebhookFormatException,
            "PG 미정산은 재전송으로 복구 가능해야 하므로 영구 오류로 분류하면 안 된다",
        )
        verify(exactly = 0) { creditService.addPurchasedCredits(any(), any(), any()) }
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
