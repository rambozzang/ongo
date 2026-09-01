package com.ongo.api.paddle

import com.ongo.application.paddle.PaddleWebhookFormatException
import com.ongo.application.paddle.PaddleWebhookService
import com.ongo.common.exception.UnauthorizedException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * 웹훅 응답 코드가 **재전송 여부를 결정한다.**
 *
 * ## 무엇이 깨져 있었나
 *
 * `catch (e: Exception)` 하나로 전부 400 을 돌려줬다. DB 장애나 게이트웨이 타임아웃처럼
 * 다시 시도하면 풀릴 실패까지 "요청이 잘못됐다"고 답하게 되고, 재전송이 끊기면 **결제는
 * 승인됐는데 구독 권한과 크레딧은 영영 반영되지 않는다.**
 *
 * [com.ongo.application.webhook.WebhookInboundGuard] 는 실패를 기록한 뒤 예외를 그대로
 * 다시 던지며, 그 KDoc 이 "호출자(컨트롤러)가 2xx 가 아닌 응답을 돌려줘야 PG 가
 * 재전송한다"고 못 박고 있다. 어떤 코드를 돌려줄지가 컨트롤러의 책임이라 여기서 고정한다.
 *
 * **실제 Paddle 을 부르지 않는다.** 서비스 경계를 목으로 두고 응답 코드만 본다.
 */
class PaddleWebhookControllerTest {

    private val webhookService = mockk<PaddleWebhookService>()
    private val controller = PaddleWebhookController(webhookService)

    private val body = """{"event_id":"evt_1","event_type":"transaction.completed","data":{}}"""
    private val signature = "ts=1756000000;h1=abcdef"

    @BeforeEach
    fun setUp() {
        every { webhookService.handleWebhook(any(), any()) } returns Unit
    }

    /* ── 재전송이 필요한 실패 ─────────────────────────────────────────── */

    /**
     * **핵심 회귀.** 업무·인프라 실패는 재전송으로 복구될 수 있다. 400 을 돌려주면
     * Paddle 이 재전송을 멈추고 결제만 남는다.
     */
    @Test
    @DisplayName("업무 처리 실패는 5xx 로 알려 재전송을 유도한다")
    fun businessFailureAsksForRetry() {
        every { webhookService.handleWebhook(any(), any()) } throws IllegalStateException("구독을 찾을 수 없습니다")

        val response = controller.handleWebhook(body, signature)

        assertEquals(500, response.statusCode.value(), "재전송이 끊겨 결제가 영구 누락된다")
        assertEquals(false, response.body!!.success)
    }

    /** DB·게이트웨이 장애도 같다 — 잠시 뒤 다시 오면 성공할 수 있다. */
    @Test
    @DisplayName("인프라 장애도 5xx 로 알린다")
    fun infrastructureFailureAsksForRetry() {
        every { webhookService.handleWebhook(any(), any()) } throws RuntimeException("connection refused")

        assertEquals(500, controller.handleWebhook(body, signature).statusCode.value())
    }

    /**
     * 업무 처리도 `IllegalStateException` 을 던진다. 형식 오류를 그 타입으로 좁히면
     * 위 사고가 그대로 재현되므로, **전용 타입만** 400 이어야 한다.
     */
    @Test
    @DisplayName("업무 IllegalState 를 형식 오류로 오인하지 않는다")
    fun businessIllegalStateIsNotTreatedAsAFormatError() {
        every { webhookService.handleWebhook(any(), any()) } throws
            IllegalStateException("Paddle 웹훅에 event_type 이 없어 처리할 수 없습니다")

        // 같은 문구여도 타입이 다르면 재전송 대상이다. 문자열로 판단하지 않는다.
        assertEquals(500, controller.handleWebhook(body, signature).statusCode.value())
    }

    /* ── 재전송이 무의미한 실패 ───────────────────────────────────────── */

    /** 서명이 틀리면 같은 요청을 다시 보내도 결과가 같다. */
    @Test
    @DisplayName("서명 검증 실패는 400 으로 끊는다")
    fun signatureFailureStopsRetries() {
        every { webhookService.handleWebhook(any(), any()) } throws
            UnauthorizedException("Paddle 웹훅 서명 검증 실패")

        val response = controller.handleWebhook(body, signature)

        assertEquals(400, response.statusCode.value())
        assertEquals(false, response.body!!.success)
    }

    /** 본문 형식 오류도 재전송으로 풀리지 않는다 — 같은 본문이 다시 올 뿐이다. */
    @Test
    @DisplayName("본문 형식 오류는 400 으로 끊는다")
    fun formatFailureStopsRetries() {
        every { webhookService.handleWebhook(any(), any()) } throws
            PaddleWebhookFormatException("Paddle 웹훅에 event_id 가 없어 멱등 처리를 보장할 수 없습니다")

        assertEquals(400, controller.handleWebhook(body, signature).statusCode.value())
    }

    /** 서명 헤더가 아예 없으면 서비스를 부르지도 않는다. */
    @Test
    @DisplayName("서명 헤더가 없으면 처리하지 않고 400 이다")
    fun missingSignatureHeaderIsRejectedBeforeProcessing() {
        listOf(null, "", "   ").forEach { header ->
            val response = controller.handleWebhook(body, header)

            assertEquals(400, response.statusCode.value(), "서명 없이 통과시켰다: $header")
        }
        verify(exactly = 0) { webhookService.handleWebhook(any(), any()) }
    }

    /* ── 성공 ─────────────────────────────────────────────────────────── */

    /** 성공은 200 이어야 재전송이 멈춘다 — 아니면 같은 결제가 계속 다시 온다. */
    @Test
    @DisplayName("성공하면 200 을 돌려준다")
    fun successStopsRetries() {
        val response = controller.handleWebhook(body, signature)

        assertEquals(200, response.statusCode.value())
        assertEquals(true, response.body!!.success)
        verify(exactly = 1) { webhookService.handleWebhook(body, signature) }
    }

    /** 원문 본문을 그대로 넘겨야 서명 검증이 성립한다 — 재직렬화하면 서명이 깨진다. */
    @Test
    @DisplayName("원문 본문과 서명을 그대로 서비스에 넘긴다")
    fun rawBodyAndSignatureArePassedThrough() {
        controller.handleWebhook(body, signature)

        verify(exactly = 1) { webhookService.handleWebhook(body, signature) }
    }
}
