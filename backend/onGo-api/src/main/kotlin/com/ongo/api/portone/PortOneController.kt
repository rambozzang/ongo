package com.ongo.api.portone

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.api.config.CurrentUser
import com.ongo.application.portone.PortOneCheckoutIntent
import com.ongo.application.portone.PortOnePaymentResult
import com.ongo.application.portone.PortOnePaymentService
import com.ongo.common.ResData
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/portone")
class PortOneController(
    private val service: PortOnePaymentService,
    private val objectMapper: ObjectMapper,
) {
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

    /** 웹훅 본문은 신뢰하지 않고 paymentId로 PortOne API를 재조회해 검증한다. */
    @PostMapping("/webhook")
    fun webhook(@RequestBody rawBody: String): ResponseEntity<ResData<Nothing?>> {
        val paymentId = extractPaymentId(objectMapper.readTree(rawBody))
            ?: return ResponseEntity.badRequest().body(ResData(success = false, error = "paymentId가 없습니다"))
        service.complete(null, paymentId)
        return ResData.success(null)
    }

    private fun extractPaymentId(json: JsonNode): String? = sequenceOf(
        json.path("data").path("paymentId"),
        json.path("paymentId"),
        json.path("data").path("payment_id"),
        json.path("payment_id"),
    ).mapNotNull { it.takeUnless { node -> node.isMissingNode || node.isNull }?.asText() }
        .firstOrNull()
}

data class SubscriptionCheckoutRequest(val planType: String, val billingCycle: String = "MONTHLY")
data class CreditCheckoutRequest(val packageName: String)
