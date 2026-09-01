package com.ongo.application.paddle

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.ongo.application.credit.CreditService
import com.ongo.application.subscription.DummyTransactionManagerForTest
import com.ongo.application.webhook.WebhookEventRecorder
import com.ongo.application.webhook.WebhookInboundGuard
import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import com.ongo.domain.payment.Payment
import com.ongo.domain.payment.PaymentRepository
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.UserRepository
import com.ongo.domain.webhook.WebhookEvent
import com.ongo.domain.webhook.WebhookEventRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Paddle 웹훅의 **동시성·멱등성**을 고정한다.
 *
 * 수신 기록이 업무 트랜잭션과 분리돼 먼저 커밋되므로, 같은 이벤트의 두 번째 전달도
 * `PROCESSED` 가 아닌 행을 보고 업무 처리를 함께 시작한다. 환불 크레딧 회수처럼 멱등하지
 * 않은 처리가 두 번 실행되면 사용자 크레딧이 이중으로 깎인다.
 */
class PaddleWebhookConcurrencyTest {

    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    private val gateway = mockk<PaddleGateway>(relaxed = true)
    private val subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true)
    private val paymentRepository = mockk<PaymentRepository>(relaxed = true)
    private val creditService = mockk<CreditService>(relaxed = true)
    private val webhookEventRepository = mockk<WebhookEventRepository>(relaxed = true)

    private val service = PaddleWebhookService(
        gateway,
        subscriptionRepository,
        paymentRepository,
        mockk<UserRepository>(relaxed = true),
        creditService,
        objectMapper,
        webhookEventRepository,
        WebhookInboundGuard(
            webhookEventRepository,
            WebhookEventRecorder(webhookEventRepository, DummyTransactionManagerForTest()),
            DummyTransactionManagerForTest(),
        ),
    )

    private val signature = "ts=${System.currentTimeMillis() / 1000};h1=sig"

    private fun refundBody(eventId: String, transactionId: String) = """
        {"event_id":"$eventId","event_type":"transaction.refunded","data":{"id":"$transactionId"}}
    """.trimIndent()

    private fun creditPayment(status: PaymentStatus) = Payment(
        id = 300L,
        userId = 60L,
        type = PaymentType.CREDIT,
        amount = 4_900, // CreditPackage.STARTER
        status = status,
        paddleTransactionId = "txn_1",
    )

    private fun pendingEvent(status: String = "PENDING") = WebhookEvent(
        id = 1L,
        eventId = "evt_1",
        eventType = "transaction.refunded",
        payload = refundBody("evt_1", "txn_1"),
        status = status,
    )

    private fun acceptSignature(body: String) {
        every { gateway.verifyWebhookSignature(body, signature) } returns true
    }

    // ── 동시 전달 직렬화 ──────────────────────────────────────────────────────

    /**
     * **이 테스트가 이번 수정의 핵심이다.**
     *
     * 상대가 먼저 처리를 끝냈다는 사실은 잠금을 얻기 전에는 보이지 않는다. 잠금 뒤에 상태를
     * 다시 확인하지 않으면 두 전달이 같은 환불을 각각 반영해 크레딧을 두 번 회수한다.
     */
    @Test
    @DisplayName("잠금 획득 후 이미 완료된 이벤트면 업무 처리를 하지 않는다 — 이중 회수 방지")
    fun skipsBusinessWorkWhenAnotherDeliveryFinishedFirst() {
        val body = refundBody("evt_1", "txn_1")
        acceptSignature(body)
        // 빠른 경로에서는 아직 PENDING 으로 보인다.
        every { webhookEventRepository.findByEventId("evt_1") } returns pendingEvent()
        // 잠금을 기다리는 동안 상대가 완료했다.
        every { webhookEventRepository.findByEventIdForUpdate("evt_1") } returns pendingEvent("PROCESSED")

        service.handleWebhook(body, signature)

        verify(exactly = 0) { paymentRepository.update(any()) }
        verify(exactly = 0) { creditService.revokeCredits(any(), any(), any()) }
        verify(exactly = 0) { webhookEventRepository.markProcessed(any(), any()) }
    }

    @Test
    @DisplayName("재처리도 잠금 후 완료를 확인하면 아무것도 하지 않는다 — 스케줄러와 인바운드 경합")
    fun reprocessSkipsAlreadyProcessedEvent() {
        every { webhookEventRepository.findByEventIdForUpdate("evt_1") } returns pendingEvent("PROCESSED")

        service.reprocessWebhookEvent(pendingEvent())

        verify(exactly = 0) { paymentRepository.findByPaddleTransactionId(any()) }
        verify(exactly = 0) { creditService.revokeCredits(any(), any(), any()) }
    }

    // ── 환불 멱등성 ──────────────────────────────────────────────────────────

    /**
     * 웹훅 이벤트 단위 멱등 게이트는 **같은 결제에 대한 다른 `event_id`** 를 막지 못한다.
     * 회수는 부를 때마다 그만큼 더 깎으므로 결제 상태로 한 번 더 판정해야 한다.
     */
    @Test
    @DisplayName("이미 환불된 결제는 크레딧을 다시 회수하지 않는다")
    fun alreadyRefundedPaymentIsNotRevokedAgain() {
        val body = refundBody("evt_2", "txn_1")
        acceptSignature(body)
        every { webhookEventRepository.findByEventId("evt_2") } returns null
        every { webhookEventRepository.saveIfAbsent(any()) } returns true
        every { webhookEventRepository.findByEventIdForUpdate("evt_2") } returns null
        every { paymentRepository.findByPaddleTransactionId("txn_1") } returns creditPayment(PaymentStatus.COMPLETED)
        // 잠그고 다시 읽으니 이미 환불돼 있다.
        every { paymentRepository.findByIdForUpdate(300L) } returns creditPayment(PaymentStatus.REFUNDED)

        service.handleWebhook(body, signature)

        verify(exactly = 0) { creditService.revokeCredits(any(), any(), any()) }
        verify(exactly = 0) { paymentRepository.update(any()) }
    }

    @Test
    @DisplayName("환불 판정은 잠근 뒤 다시 읽은 상태로 한다 — 잠금 없는 값으로 판정하면 동시 회수")
    fun refundDecisionUsesLockedRow() {
        val body = refundBody("evt_3", "txn_1")
        acceptSignature(body)
        every { webhookEventRepository.findByEventId("evt_3") } returns null
        every { webhookEventRepository.saveIfAbsent(any()) } returns true
        every { webhookEventRepository.findByEventIdForUpdate("evt_3") } returns null
        every { paymentRepository.findByPaddleTransactionId("txn_1") } returns creditPayment(PaymentStatus.COMPLETED)
        every { paymentRepository.findByIdForUpdate(300L) } returns creditPayment(PaymentStatus.COMPLETED)

        service.handleWebhook(body, signature)

        verify { paymentRepository.findByIdForUpdate(300L) }
        verify { creditService.revokeCredits(60L, 500, "REFUND_txn_1") }
    }

    /**
     * 예전에는 로그만 남기고 REFUNDED 를 커밋했다. 그러면 사용자가 돈을 돌려받고 크레딧도
     * 그대로 쓴다. 회수량을 지어내는 대신 롤백해 사람이 확인하게 한다.
     */
    @Test
    @DisplayName("환불 금액으로 패키지를 못 찾으면 REFUNDED 도 커밋하지 않는다")
    fun unknownRefundAmountRollsBackTheRefund() {
        val body = refundBody("evt_4", "txn_1")
        acceptSignature(body)
        every { webhookEventRepository.findByEventId("evt_4") } returns null
        every { webhookEventRepository.saveIfAbsent(any()) } returns true
        every { webhookEventRepository.findByEventIdForUpdate("evt_4") } returns null
        val odd = creditPayment(PaymentStatus.COMPLETED).copy(amount = 4_321)
        every { paymentRepository.findByPaddleTransactionId("txn_1") } returns odd
        every { paymentRepository.findByIdForUpdate(300L) } returns odd
        every { webhookEventRepository.updateIfNotProcessed(any()) } returns true

        val error = assertFailsWith<IllegalStateException> { service.handleWebhook(body, signature) }

        assertTrue(error.message!!.contains("회수량"), "실패 사유가 회수량 미확정이어야 한다: ${error.message}")
        verify(exactly = 0) { creditService.revokeCredits(any(), any(), any()) }
        // 완료 표시가 남으면 재시도도 운영 확인도 불가능해진다.
        verify(exactly = 0) { webhookEventRepository.markProcessed(any(), any()) }
    }

    // ── 필수 필드 누락의 조용한 성공 제거 ─────────────────────────────────────

    private fun assertFailsOnSilentSuccess(eventId: String, body: String, expectIn: String) {
        acceptSignature(body)
        every { webhookEventRepository.findByEventId(eventId) } returns null
        every { webhookEventRepository.saveIfAbsent(any()) } returns true
        every { webhookEventRepository.findByEventIdForUpdate(eventId) } returns null
        every { webhookEventRepository.updateIfNotProcessed(any()) } returns true

        val error = assertFailsWith<IllegalStateException> { service.handleWebhook(body, signature) }

        assertTrue(error.message!!.contains(expectIn), "실패 사유가 다르다: ${error.message}")
        // 조용한 성공의 본질은 PROCESSED 로 찍히는 것이다.
        verify(exactly = 0) { webhookEventRepository.markProcessed(any(), any()) }
    }

    @Test
    @DisplayName("결제 완료에 user_id 가 없으면 완료로 찍지 않는다 — 돈만 받고 기록 없음 방지")
    fun completedWithoutUserIdFails() {
        assertFailsOnSilentSuccess(
            "evt_no_user",
            """{"event_id":"evt_no_user","event_type":"transaction.completed","data":{"id":"txn_9"}}""",
            "user_id",
        )
    }

    @Test
    @DisplayName("알 수 없는 price id 는 FREE 로 강등하지 않고 실패로 남긴다")
    fun unknownPriceIdFailsInsteadOfDowngrading() {
        every { gateway.getPriceIdForPlan(any(), any()) } returns "pri_known"
        assertFailsOnSilentSuccess(
            "evt_unknown_price",
            """{"event_id":"evt_unknown_price","event_type":"subscription.created",""" +
                """"data":{"id":"sub_1","customer_id":"ctm_1","custom_data":{"user_id":1},""" +
                """"items":[{"price":{"id":"pri_brand_new"}}]}}""",
            "요금제",
        )
    }

    /**
     * **깨진 JSON 은 형식 오류다.**
     *
     * 감싸지 않으면 Jackson 의 `JsonProcessingException` 이 컨트롤러의 마지막 `catch` 로 가
     * 5xx 가 되고, Paddle 은 못 고칠 본문을 계속 재전송한다. 더 나쁜 것은 그 실패가 인프라
     * 장애처럼 보여, 우리가 모르는 본문 형식이 왔다는 신호가 재시도 잡음에 묻힌다는 점이다.
     *
     * 서명은 통과한 상태로 둔다 — 서명 실패로 걸린 것이 아니라 **본문 해석에서** 걸렸음을
     * 분명히 하기 위해서다.
     */
    @Test
    @DisplayName("깨진 JSON 은 형식 오류로 분류한다 — 재전송해도 같은 바이트다")
    fun malformedJsonIsAFormatError() {
        val body = """{"event_id":"evt_1","event_type":"transaction.completed","data":{"""
        acceptSignature(body)

        val error = assertFailsWith<PaddleWebhookFormatException> { service.handleWebhook(body, signature) }

        // 사유가 "해석할 수 없다"여야 한다. 파싱 실패를 삼키고 빈 맵으로 넘기면 뒤따르는
        // 필드 검사가 대신 걸려, 실제 원인(본문 자체가 깨짐)이 로그에서 사라진다.
        assertTrue(
            error.message!!.contains("해석할 수 없습니다"),
            "파싱 실패가 다른 사유로 보고됐다: ${error.message}",
        )
    }

    /** 유효한 JSON 이어도 객체가 아니면 우리가 아는 형식이 아니다. */
    @Test
    @DisplayName("객체가 아닌 본문도 형식 오류다")
    fun nonObjectBodyIsAFormatError() {
        listOf("[]", "\"just-a-string\"", "123").forEach { body ->
            acceptSignature(body)

            assertFailsWith<PaddleWebhookFormatException>("$body 를 통과시켰다") {
                service.handleWebhook(body, signature)
            }
        }
    }

    /**
     * **해석하지 못한 본문으로는 아무것도 하지 않는다.** 멱등 키를 만들지도, 결제를
     * 저장하지도, 크레딧을 주지도 않는다 — 무엇을 하려는 이벤트인지조차 모르는 상태다.
     */
    @Test
    @DisplayName("깨진 본문은 어떤 업무 부수효과도 남기지 않는다")
    fun malformedBodyLeavesNoSideEffect() {
        val body = """{"event_id":"evt_1",""" // 중간에서 끊긴 본문
        acceptSignature(body)

        assertFailsWith<PaddleWebhookFormatException> { service.handleWebhook(body, signature) }

        verify(exactly = 0) { webhookEventRepository.saveIfAbsent(any()) }
        verify(exactly = 0) { webhookEventRepository.markProcessed(any(), any()) }
        verify(exactly = 0) { paymentRepository.save(any()) }
        verify(exactly = 0) { creditService.addPurchasedCredits(any(), any(), any()) }
        verify(exactly = 0) { creditService.applyPlanEntitlement(any(), any(), any()) }
        verify(exactly = 0) { subscriptionRepository.save(any()) }
    }

    /**
     * **파싱 자리의 `catch` 는 형식 오류만 잡아야 한다.**
     *
     * 넓게 잡으면 그 자리에서 난 다른 실패까지 400 이 되어, 재전송으로 복구될 수 있는
     * 실패가 "본문이 잘못됐다"로 둔갑한다. 지금은 `readValue` 하나만 감싸고 있어 실피해가
     * 없지만, 그 블록이 자라는 순간 조용히 위험해진다 — 계약을 지금 못 박는다.
     *
     * 실제 ObjectMapper 로는 형식 오류 외의 예외를 만들 수 없어, 이 테스트만 목을 쓴다.
     */
    @Test
    @DisplayName("파싱 외의 실패는 형식 오류로 바꾸지 않는다")
    fun onlyJsonFailuresBecomeFormatErrors() {
        val brokenMapper = mockk<ObjectMapper>()
        every { brokenMapper.readValue(any<String>(), any<com.fasterxml.jackson.core.type.TypeReference<*>>()) } throws
            IllegalStateException("역직렬화 설정 오류")
        val withBrokenMapper = PaddleWebhookService(
            gateway,
            subscriptionRepository,
            paymentRepository,
            mockk<UserRepository>(relaxed = true),
            creditService,
            brokenMapper,
            webhookEventRepository,
            WebhookInboundGuard(
                webhookEventRepository,
                WebhookEventRecorder(webhookEventRepository, DummyTransactionManagerForTest()),
                DummyTransactionManagerForTest(),
            ),
        )
        val body = """{"event_id":"evt_1","event_type":"transaction.completed","data":{}}"""
        acceptSignature(body)

        val error = assertFailsWith<IllegalStateException> { withBrokenMapper.handleWebhook(body, signature) }

        assertTrue(
            error !is PaddleWebhookFormatException,
            "형식 오류가 아닌 실패를 400 대상으로 바꿨다: ${error::class.simpleName}",
        )
    }

    /**
     * 서명 검증이 **먼저**다. 본문이 깨졌어도 서명을 통과하지 못했다면 그 사유로 끊어야
     * 한다 — 서명 실패와 형식 오류는 둘 다 400 이지만 원인이 다르고, 순서가 뒤집히면
     * 서명 없는 본문을 파싱하게 된다.
     */
    @Test
    @DisplayName("서명 실패가 형식 오류보다 먼저 걸린다")
    fun signatureIsCheckedBeforeParsing() {
        val body = """{"broken"""
        every { gateway.verifyWebhookSignature(body, signature) } returns false

        assertFailsWith<com.ongo.common.exception.UnauthorizedException> {
            service.handleWebhook(body, signature)
        }
    }

    @Test
    @DisplayName("해석할 수 없는 본문은 200 으로 삼키지 않는다 — Paddle 이 재시도하게 둔다")
    fun unparseableBodyIsNotSwallowed() {
        val body = """{"event_id":"evt_shape","event_type":"transaction.completed"}"""
        acceptSignature(body)

        // 형식 오류는 전용 타입이다. 업무 실패(IllegalStateException)와 섞이면 컨트롤러가
        // 재전송이 필요한 실패까지 400 으로 끊는다 — PaddleWebhookController 참고.
        val error = assertFailsWith<PaddleWebhookFormatException> { service.handleWebhook(body, signature) }

        assertTrue(error.message!!.contains("data"), "실패 사유가 다르다: ${error.message}")
        verify(exactly = 0) { webhookEventRepository.markProcessed(any(), any()) }
    }

    @Test
    @DisplayName("구독 취소 대상 구독이 없으면 조용히 넘기지 않는다")
    fun canceledWithoutSubscriptionFails() {
        every { subscriptionRepository.findByPaddleSubscriptionId("sub_missing") } returns null
        assertFailsOnSilentSuccess(
            "evt_cancel_missing",
            """{"event_id":"evt_cancel_missing","event_type":"subscription.canceled","data":{"id":"sub_missing"}}""",
            "찾을 수 없습니다",
        )
    }

    @Test
    @DisplayName("환불 대상 결제가 없으면 조용히 넘기지 않는다")
    fun refundWithoutPaymentFails() {
        every { paymentRepository.findByPaddleTransactionId("txn_missing") } returns null
        assertFailsOnSilentSuccess(
            "evt_refund_missing",
            refundBody("evt_refund_missing", "txn_missing"),
            "찾을 수 없습니다",
        )
    }

    // ── 멱등 키 보호 ─────────────────────────────────────────────────────────

    /**
     * `event_id` 는 멱등 키 그 자체다. 없다고 새로 만들면 같은 이벤트의 재전송마다 다른 키가
     * 생겨 **멱등 게이트를 매번 통과한다** — 결제 하나가 여러 번 반영되고 크레딧도 그만큼
     * 중복 지급된다.
     */
    @Test
    @DisplayName("event_id 가 없으면 키를 지어내지 않고 실패로 남긴다 — 중복 지급 방지")
    fun missingEventIdIsNotFabricated() {
        val body = """{"event_type":"transaction.completed","data":{"id":"txn_1","custom_data":{"user_id":1}}}"""
        acceptSignature(body)

        val error = assertFailsWith<PaddleWebhookFormatException> { service.handleWebhook(body, signature) }

        assertTrue(error.message!!.contains("event_id"), "실패 사유가 다르다: ${error.message}")
        // 키를 지어냈다면 여기서 새 행이 만들어졌을 것이다.
        verify(exactly = 0) { webhookEventRepository.saveIfAbsent(any()) }
        verify(exactly = 0) { paymentRepository.save(any()) }
        verify(exactly = 0) { creditService.addPurchasedCredits(any(), any(), any()) }
    }

    @Test
    @DisplayName("공백 event_id·event_type 도 허용하지 않는다")
    fun blankIdentifiersAreRejected() {
        val blankId = """{"event_id":"  ","event_type":"transaction.completed","data":{"id":"txn_1"}}"""
        acceptSignature(blankId)
        assertTrue(
            assertFailsWith<PaddleWebhookFormatException> { service.handleWebhook(blankId, signature) }
                .message!!.contains("event_id"),
        )

        val blankType = """{"event_id":"evt_1","event_type":"","data":{"id":"txn_1"}}"""
        acceptSignature(blankType)
        assertTrue(
            assertFailsWith<PaddleWebhookFormatException> { service.handleWebhook(blankType, signature) }
                .message!!.contains("event_type"),
        )

        verify(exactly = 0) { webhookEventRepository.saveIfAbsent(any()) }
    }

    /**
     * 소유권 판정은 `event_type` **컬럼**으로 하는데 분기는 **페이로드**로 한다. 둘이 어긋나면
     * Paddle 소유로 분류돼 뽑힌 행이 실제로는 다른 타입으로 처리된다.
     */
    @Test
    @DisplayName("저장된 event_type 과 페이로드가 다르면 재처리하지 않는다")
    fun reprocessRejectsEventTypeMismatch() {
        every { webhookEventRepository.findByEventIdForUpdate("evt_mismatch") } returns null

        val error = assertFailsWith<IllegalStateException> {
            service.reprocessWebhookEvent(
                WebhookEvent(
                    id = 1L,
                    eventId = "evt_mismatch",
                    eventType = "transaction.completed",
                    payload = """{"event_type":"transaction.refunded","data":{"id":"txn_1"}}""",
                ),
            )
        }

        assertTrue(error.message!!.contains("다릅니다"), "실패 사유가 다르다: ${error.message}")
        verify(exactly = 0) { paymentRepository.findByPaddleTransactionId(any()) }
    }

    @Test
    @DisplayName("재처리 페이로드에 event_type 이 없으면 성공으로 끝내지 않는다 — 스케줄러가 PROCESSED 로 찍는다")
    fun reprocessWithoutEventTypeFails() {
        every { webhookEventRepository.findByEventIdForUpdate("evt_broken") } returns null

        val error = assertFailsWith<IllegalStateException> {
            service.reprocessWebhookEvent(
                WebhookEvent(id = 1L, eventId = "evt_broken", eventType = "transaction.completed", payload = "{}"),
            )
        }

        assertTrue(error.message!!.contains("event_type"), "실패 사유가 다르다: ${error.message}")
    }

    /** 인바운드와 재처리 **양쪽 모두** 형식이 깨진 페이로드를 성공으로 끝내면 안 된다. */
    @Test
    @DisplayName("재처리 페이로드에 data 가 없어도 성공으로 끝내지 않는다")
    fun reprocessWithoutDataFails() {
        every { webhookEventRepository.findByEventIdForUpdate("evt_nodata") } returns null

        val error = assertFailsWith<IllegalStateException> {
            service.reprocessWebhookEvent(
                WebhookEvent(
                    id = 1L,
                    eventId = "evt_nodata",
                    eventType = "transaction.completed",
                    payload = """{"event_type":"transaction.completed"}""",
                ),
            )
        }

        assertTrue(error.message!!.contains("data"), "실패 사유가 다르다: ${error.message}")
        verify(exactly = 0) { paymentRepository.findByPaddleTransactionId(any()) }
    }
}
