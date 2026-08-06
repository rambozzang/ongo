package com.ongo.api.portone

import com.ongo.api.config.CurrentUser
import com.ongo.application.portone.PortOneCheckoutIntent
import com.ongo.application.portone.PortOnePaymentResult
import com.ongo.application.portone.PortOnePaymentService
import com.ongo.application.portone.PortOneWebhookFormatException
import com.ongo.common.ResData
import com.ongo.common.exception.UnauthorizedException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/portone")
class PortOneController(
    private val service: PortOnePaymentService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/checkout/subscription")
    fun createSubscriptionCheckout(
        @CurrentUser userId: Long,
        @RequestBody request: SubscriptionCheckoutRequest,
    ): ResData<PortOneCheckoutIntent> = ResData(data = service.createSubscriptionCheckout(userId, request.planType, request.billingCycle))

    @PostMapping("/checkout/credit")
    fun createCreditCheckout(
        @CurrentUser userId: Long,
        @RequestBody request: CreditCheckoutRequest,
    ): ResData<PortOneCheckoutIntent> = ResData(data = service.createCreditCheckout(userId, request.packageName))

    @PostMapping("/payments/{paymentId}/complete")
    fun complete(
        @CurrentUser userId: Long,
        @PathVariable paymentId: String,
    ): ResData<PortOnePaymentResult> = ResData(data = service.complete(userId, paymentId))

    /**
     * 포트원 웹훅 수신 엔드포인트.
     *
     * Standard Webhooks 서명을 먼저 검증하고, 본문은 신뢰하지 않고 paymentId로 PortOne API를
     * 재조회해 결제 상태·금액을 확인한다. 포트원은 2xx가 아니면 최대 5회 재전송하므로,
     * 검증 실패만 400을 반환하고 처리 대상이 아닌 이벤트는 200으로 응답한다.
     */
    @PostMapping("/webhook")
    fun webhook(
        @RequestBody rawBody: String,
        @RequestHeader(name = PortOneWebhookHeaders.ID, required = false) webhookId: String?,
        @RequestHeader(name = PortOneWebhookHeaders.SIGNATURE, required = false) webhookSignature: String?,
        @RequestHeader(name = PortOneWebhookHeaders.TIMESTAMP, required = false) webhookTimestamp: String?,
    ): ResponseEntity<ResData<Nothing?>> = try {
        service.handleWebhook(rawBody, webhookId, webhookSignature, webhookTimestamp)
        ResponseEntity.ok(ResData(success = true, data = null))
    } catch (e: UnauthorizedException) {
        // 서명 검증 실패 — 재전송해도 결과가 같으므로 400으로 끊는다
        log.warn("포트원 웹훅 서명 검증 실패: {}", e.message)
        ResponseEntity.badRequest().body(ResData(success = false, error = e.message))
    } catch (e: PortOneWebhookFormatException) {
        // 본문 형식 오류 — 역시 재전송으로 해결되지 않는다.
        // 반드시 이 전용 예외로만 좁혀야 한다. IllegalArgumentException 전체를 400으로 묶으면
        // complete()의 결제 상태·금액·통화 검증 실패(require)까지 400이 되어,
        // PG가 아직 PAID를 반영하지 않은 일시 상태에서 재전송이 끊기고 결제가 영구 누락된다.
        log.warn("포트원 웹훅 본문 오류: {}", e.message)
        ResponseEntity.badRequest().body(ResData(success = false, error = e.message))
    } catch (e: Exception) {
        // PG 상태 미반영·금액 불일치·게이트웨이/DB 장애 등 — 트랜잭션은 이미 롤백됐다.
        // 5xx를 돌려줘야 포트원이 재전송(최대 5회)해 복구할 수 있다.
        log.error("포트원 웹훅 처리 실패 — 재전송 유도를 위해 5xx 반환", e)
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ResData(success = false, error = e.message))
    }
}

/** Standard Webhooks 규격 헤더명. */
object PortOneWebhookHeaders {
    const val ID = "webhook-id"
    const val SIGNATURE = "webhook-signature"
    const val TIMESTAMP = "webhook-timestamp"
}

data class SubscriptionCheckoutRequest(val planType: String, val billingCycle: String = "MONTHLY")
data class CreditCheckoutRequest(val packageName: String)
