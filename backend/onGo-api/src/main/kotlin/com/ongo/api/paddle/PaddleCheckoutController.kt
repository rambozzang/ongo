package com.ongo.api.paddle

import com.ongo.application.paddle.PaddleCheckoutData
import com.ongo.application.paddle.PaddleCheckoutService
import com.ongo.application.paddle.PaddleConfigResponse
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Parameter
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/paddle")
class PaddleCheckoutController(
    private val checkoutService: PaddleCheckoutService,
) {

    @GetMapping("/config")
    fun getConfig(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
    ): ResData<PaddleConfigResponse> {
        val config = checkoutService.getConfig(userId)
        return ResData(data = config)
    }

    @PostMapping("/checkout/subscription")
    fun createSubscriptionCheckout(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @RequestBody request: SubscriptionCheckoutRequest,
    ): ResData<PaddleCheckoutData> {
        val data = checkoutService.createSubscriptionCheckout(userId, request.planType, request.billingCycle)
        return ResData(data = data)
    }

    @PostMapping("/checkout/credit")
    fun createCreditCheckout(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @RequestBody request: CreditCheckoutRequest,
    ): ResData<PaddleCheckoutData> {
        val data = checkoutService.createCreditCheckout(userId, request.packageName)
        return ResData(data = data)
    }
}

/**
 * 연/월 구분이 없으면 항상 월간 가격으로 결제된다. 화면에는 연간 토글과 연간 가격이
 * 표시되므로 이 값을 받지 않으면 사용자가 본 금액과 실제 청구가 어긋난다.
 */
data class SubscriptionCheckoutRequest(
    val planType: String,
    val billingCycle: String = "MONTHLY",
)
data class CreditCheckoutRequest(val packageName: String)
