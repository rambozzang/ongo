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
        val data = checkoutService.createSubscriptionCheckout(userId, request.planType)
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

data class SubscriptionCheckoutRequest(val planType: String)
data class CreditCheckoutRequest(val packageName: String)
