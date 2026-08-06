package com.ongo.api.portone

import com.ongo.application.portone.PortOnePaymentService
import com.ongo.application.portone.PortOneWebhookFormatException
import com.ongo.common.exception.UnauthorizedException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 포트원 웹훅 응답 코드 분류 테스트.
 *
 * 포트원은 2xx가 아니면 최대 5회 재전송하고 그 뒤 포기한다. 따라서 응답 코드 선택이
 * 곧 복구 가능성이다.
 *
 * - **400**: 재전송해도 결과가 같은 영구 오류 (서명 위조, 본문 형식 오류)
 * - **200**: 처리 대상이 아닌 이벤트
 * - **500**: 일시적 실패 (PG 미정산, 게이트웨이 장애, DB 장애) — 재전송으로 복구 가능해야 한다
 *
 * 특히 결제 상태·금액 검증 실패를 400으로 돌려주면 포트원이 재전송을 멈춰
 * **결제와 크레딧 지급이 영구 누락**된다. 이 테스트가 그 회귀를 막는다.
 */
class PortOneControllerWebhookResponseTest {

    private val service = mockk<PortOnePaymentService>()
    private val controller = PortOneController(service)

    private val body = """{"type":"Transaction.Paid","data":{"paymentId":"ongo-42"}}"""
    private val webhookId = "webhook-1"
    private val signature = "v1,sig"
    private val timestamp = "1700000000"

    private fun callWebhook() =
        controller.webhook(body, webhookId, signature, timestamp).statusCode.value()

    private fun serviceThrows(e: Throwable) {
        every { service.handleWebhook(any(), any(), any(), any()) } throws e
    }

    @Test
    @DisplayName("정상 처리되면 200을 반환한다")
    fun successReturns200() {
        every { service.handleWebhook(any(), any(), any(), any()) } returns Unit

        assertEquals(200, callWebhook())
    }

    @Test
    @DisplayName("서명 검증 실패는 400 — 재전송해도 결과가 같다")
    fun signatureFailureReturns400() {
        serviceThrows(UnauthorizedException("포트원 웹훅 서명 검증 실패"))

        assertEquals(400, callWebhook())
    }

    @Test
    @DisplayName("본문 형식 오류는 400 — 재전송해도 결과가 같다")
    fun malformedBodyReturns400() {
        serviceThrows(PortOneWebhookFormatException("포트원 웹훅 본문을 해석할 수 없습니다"))

        assertEquals(400, callWebhook())
    }

    @Test
    @DisplayName("PG가 아직 PAID를 반영하지 않았으면 500 — 400이면 재전송이 끊겨 결제가 영구 누락된다")
    fun pgNotYetPaidReturns500() {
        // complete() 의 require(verified.status == "PAID") 가 던지는 예외
        serviceThrows(IllegalArgumentException("포트원 결제가 완료되지 않았습니다: READY"))

        assertEquals(500, callWebhook())
    }

    @Test
    @DisplayName("금액 불일치는 500 — 조용히 400으로 삼키면 안 된다")
    fun amountMismatchReturns500() {
        serviceThrows(IllegalArgumentException("결제 금액이 일치하지 않습니다"))

        assertEquals(500, callWebhook())
    }

    @Test
    @DisplayName("통화 불일치는 500")
    fun currencyMismatchReturns500() {
        serviceThrows(IllegalArgumentException("결제 통화가 일치하지 않습니다"))

        assertEquals(500, callWebhook())
    }

    @Test
    @DisplayName("게이트웨이·DB 장애는 500 — 재전송으로 복구 가능해야 한다")
    fun infrastructureFailureReturns500() {
        serviceThrows(IllegalStateException("포트원 결제 조회 응답이 없습니다"))

        assertEquals(500, callWebhook())
    }
}
