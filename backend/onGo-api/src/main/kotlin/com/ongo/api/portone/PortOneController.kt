package com.ongo.api.portone

import com.ongo.api.config.CurrentUser
import com.ongo.application.portone.PortOneCheckoutIntent
import com.ongo.application.portone.PortOnePaymentResult
import com.ongo.application.portone.PortOnePaymentService
import com.ongo.common.ResData
import org.slf4j.LoggerFactory
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
    } catch (e: Exception) {
        log.warn("포트원 웹훅 처리 실패: {}", e.message)
        ResponseEntity.badRequest().body(ResData(success = false, error = e.message))
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
