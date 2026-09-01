package com.ongo.application.portone

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.credit.CreditService
import com.ongo.application.paddle.PaddleWebhookService
import com.ongo.application.subscription.DummyTransactionManagerForTest
import com.ongo.application.webhook.WebhookEventRecorder
import com.ongo.application.webhook.WebhookInboundGuard
import com.ongo.application.webhook.WebhookRetryRunner
import com.ongo.common.enums.CreditPackage
import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import com.ongo.domain.lock.DistributedLockPort
import com.ongo.domain.payment.Payment
import com.ongo.domain.payment.PaymentRepository
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.UserRepository
import com.ongo.domain.webhook.WebhookEvent
import com.ongo.domain.webhook.WebhookEventRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 포트원 웹훅의 **자체 재시도 경로**를 고정한다.
 *
 * 예전에는 이 경로가 아예 없었다. 수신 기록(`saveIfAbsent`)이 업무 트랜잭션 안에 있어서
 * 처리에 실패하면 행까지 함께 롤백됐고, 재시도 대상도 DEAD_LETTER 도 운영자가 볼 이력도
 * 남지 않았다. 복구가 전적으로 포트원 자체 재전송에 달려 있었고 그마저 소진되면 결제·취소가
 * 반영되지 않은 채 사라졌다.
 */
class PortOneWebhookRetryTest {

    private val paymentRepository = mockk<PaymentRepository>(relaxed = true)
    private val subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true)
    private val creditService = mockk<CreditService>(relaxed = true)
    private val gateway = mockk<PortOnePaymentGateway>(relaxed = true)
    private val webhookEventRepository = mockk<WebhookEventRepository>(relaxed = true)
    private val lockPort = mockk<DistributedLockPort>()

    private val service = PortOnePaymentService(
        paymentRepository = paymentRepository,
        subscriptionRepository = subscriptionRepository,
        userRepository = mockk<UserRepository>(relaxed = true),
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
        eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true),
        webhookInboundGuard = WebhookInboundGuard(
            webhookEventRepository,
            WebhookEventRecorder(webhookEventRepository, DummyTransactionManagerForTest()),
            DummyTransactionManagerForTest(),
        ),
    )

    private val scheduler = PortOneWebhookRetryScheduler(
        WebhookRetryRunner(webhookEventRepository, lockPort),
        service,
    )

    private fun lockAcquired() {
        every { lockPort.withLock(any(), any()) } answers {
            secondArg<() -> Unit>().invoke()
            true
        }
    }

    private fun storedEvent(
        type: String = "Transaction.Paid",
        paymentId: String = "ongo-42",
        status: String = "FAILED",
    ) = WebhookEvent(
        id = 1L,
        eventId = "portone:webhook-1",
        eventType = type,
        payload = """{"type":"$type","data":{"paymentId":"$paymentId"}}""",
        status = status,
        retryCount = 1,
        nextRetryAt = LocalDateTime.now().minusMinutes(1),
    )

    // ── 수신·실패 기록의 내구성 ───────────────────────────────────────────────

    /**
     * **이 테스트가 이번 수정의 핵심이다.** 실패 기록이 업무 트랜잭션 안에 있으면 `throw` 와
     * 함께 롤백되어 재시도 대상이 사라진다.
     */
    @Test
    @DisplayName("처리에 실패해도 수신·실패 기록은 별도 트랜잭션에 남는다")
    fun failureLeavesADurableRetryRecord() {
        every { gateway.verifyWebhookSignature(any(), any(), any(), any()) } returns true
        every { webhookEventRepository.findByEventId(any()) } returns null
        every { webhookEventRepository.saveIfAbsent(any()) } returns true
        every { webhookEventRepository.findByEventIdForUpdate(any()) } returns null
        every { paymentRepository.findByIdForUpdate(42) } returns null // 결제 미발견 → 실패
        val recorded = slot<WebhookEvent>()
        every { webhookEventRepository.updateIfNotProcessed(capture(recorded)) } returns true

        assertFailsWith<Exception> {
            service.handleWebhook(
                """{"type":"Transaction.Paid","data":{"paymentId":"ongo-42"}}""",
                "webhook-1", "v1,sig", "1700000000",
            )
        }

        assertEquals("FAILED", recorded.captured.status)
        assertTrue(recorded.captured.nextRetryAt != null, "재시도 시각이 없으면 findRetryable 이 못 잡는다")
        // 성공 표시가 남으면 재처리 대상에서 빠진다.
        verify(exactly = 0) { webhookEventRepository.markProcessed(any(), any()) }
    }

    // ── 소유권 격리 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("포트원 스케줄러는 자기 타입만 재처리 범위로 삼는다")
    fun schedulerScopesToPortOneOwnedTypes() {
        lockAcquired()
        every { webhookEventRepository.recoverStalePending(any(), any(), any(), any()) } returns 0
        val types = slot<Set<String>>()
        every { webhookEventRepository.findRetryable(any(), capture(types)) } returns emptyList()

        scheduler.retryFailedWebhooks()

        assertEquals(PortOnePaymentService.REPROCESSABLE_EVENT_TYPES, types.captured)
        PaddleWebhookService.REPROCESSABLE_EVENT_TYPES.forEach {
            assertTrue(it !in types.captured, "Paddle 이벤트 타입 $it 이 포트원 재처리 범위에 있다")
        }
    }

    /**
     * 두 스케줄러가 같은 분산 락을 쓰면 한쪽이 도는 동안 다른 쪽이 통째로 스킵된다.
     * 그러면 한 PG 의 재시도가 영원히 밀릴 수 있다.
     */
    @Test
    @DisplayName("Paddle 스케줄러와 다른 락 슬롯을 쓴다")
    fun usesADistinctLockSlotFromPaddle() {
        val portOneLock = PortOneWebhookRetryScheduler::class.java.name.hashCode().toLong()
        val paddleLock =
            com.ongo.application.paddle.WebhookRetryScheduler::class.java.name.hashCode().toLong()

        assertTrue(portOneLock != paddleLock, "두 스케줄러가 같은 락을 쓴다")
    }

    // ── 저장 본문 재처리 ─────────────────────────────────────────────────────

    /**
     * 서명을 다시 검증하지 않는 것이 안전한 이유는 **저장 본문을 신뢰하지 않기** 때문이다.
     * 본문에서 꺼내는 것은 `type` 과 `paymentId` 뿐이고, 반영 여부는 포트원 재조회 결과가
     * 정한다. 여기서는 재조회가 PAID 를 돌려줄 때만 완료 처리되는지 본다.
     */
    @Test
    @DisplayName("재처리는 저장 본문이 아니라 포트원 재조회 결과로 판정한다")
    fun reprocessTrustsTheApiNotTheStoredBody() {
        every { webhookEventRepository.findByEventIdForUpdate(any()) } returns null
        val payment = Payment(
            id = 42, userId = 7, type = PaymentType.CREDIT,
            amount = CreditPackage.BASIC.price, currency = "KRW",
            status = PaymentStatus.PENDING, pgProvider = "portone", description = "CREDIT|BASIC",
        )
        every { paymentRepository.findByIdForUpdate(42) } returns payment
        every { paymentRepository.update(any()) } returns payment
        // 본문은 Paid 라고 하지만 포트원은 아직 준비중이라고 답한다.
        every { gateway.getPayment("ongo-42") } returns PortOnePayment(
            paymentId = "ongo-42", status = "READY",
            amount = CreditPackage.BASIC.price, currency = "KRW",
            transactionId = null, paymentMethod = null, receiptUrl = null,
        )

        assertFailsWith<IllegalArgumentException> { service.reprocessWebhookEvent(storedEvent()) }

        verify(exactly = 0) { creditService.addPurchasedCredits(any(), any(), any()) }
    }

    @Test
    @DisplayName("재처리 전에 이미 완료됐으면 아무것도 하지 않는다 — 인바운드와의 경합")
    fun reprocessSkipsAlreadyProcessed() {
        every { webhookEventRepository.findByEventIdForUpdate("portone:webhook-1") } returns
            storedEvent(status = "PROCESSED")

        service.reprocessWebhookEvent(storedEvent())

        verify(exactly = 0) { gateway.getPayment(any()) }
        verify(exactly = 0) { paymentRepository.findByIdForUpdate(any()) }
    }

    @Test
    @DisplayName("저장된 event_type 과 본문이 다르면 재처리하지 않는다")
    fun reprocessRejectsTypeMismatch() {
        every { webhookEventRepository.findByEventIdForUpdate(any()) } returns null
        val tampered = storedEvent().copy(
            eventType = "Transaction.Cancelled",
            payload = """{"type":"Transaction.Paid","data":{"paymentId":"ongo-42"}}""",
        )

        val error = assertFailsWith<IllegalStateException> { service.reprocessWebhookEvent(tampered) }

        assertTrue(error.message!!.contains("다르다"), "실패 사유가 다르다: ${error.message}")
        verify(exactly = 0) { gateway.getPayment(any()) }
    }

    @Test
    @DisplayName("저장 본문에 paymentId 가 없으면 성공으로 끝내지 않는다 — 스케줄러가 PROCESSED 로 찍는다")
    fun reprocessWithoutPaymentIdFails() {
        every { webhookEventRepository.findByEventIdForUpdate(any()) } returns null
        val broken = storedEvent().copy(payload = """{"type":"Transaction.Paid","data":{}}""")

        assertFailsWith<PortOneWebhookFormatException> { service.reprocessWebhookEvent(broken) }

        verify(exactly = 0) { gateway.getPayment(any()) }
    }
}
