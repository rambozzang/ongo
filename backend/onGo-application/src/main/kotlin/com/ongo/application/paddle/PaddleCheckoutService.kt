package com.ongo.application.paddle

import com.ongo.application.credit.CreditService
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.user.UserRepository
import org.springframework.stereotype.Service

@Service
class PaddleCheckoutService(
    private val paddleGateway: PaddleGateway,
    private val userRepository: UserRepository,
    /**
     * 결제창을 열기 전 크레딧 원장 존재를 확인하는 데만 쓴다.
     * [CreditService.ensureAccountPresence] 는 읽기 전용이며 아무것도 만들지 않는다.
     */
    private val creditService: CreditService,
) {

    fun getConfig(userId: Long): PaddleConfigResponse {
        val user = userRepository.findById(userId)
            ?: throw NotFoundException("사용자", userId)
        return PaddleConfigResponse(
            clientToken = paddleGateway.getClientToken(),
            environment = paddleGateway.getEnvironment(),
            paddleCustomerId = user.paddleCustomerId,
        )
    }

    fun createSubscriptionCheckout(
        userId: Long,
        planType: String,
        billingCycle: String = "MONTHLY",
    ): PaddleCheckoutData {
        val user = userRepository.findById(userId)
            ?: throw NotFoundException("사용자", userId)
        /*
         * **가격 조회보다 먼저** 막는다.
         *
         * Paddle 결제 완료(`handleSubscriptionCreated`)는 `applyPlanEntitlement` 를 부르고,
         * 원장이 없으면 거기서 웹훅 처리가 실패한다 — 그 시점에는 이미 청구가 끝나 있다.
         * 여기서 끝내면 가격 ID 조회도, 프론트에 넘길 체크아웃 데이터 생성도 일어나지 않아
         * 결제창 자체가 열리지 않는다.
         */
        creditService.ensureAccountPresence(userId)
        val priceId = paddleGateway.getPriceIdForPlan(planType, billingCycle)
            ?: throw IllegalArgumentException(
                "해당 플랜의 Paddle 가격 ID를 찾을 수 없습니다: $planType ($billingCycle)"
            )
        return PaddleCheckoutData(
            priceId = priceId,
            customData = mapOf("user_id" to userId),
            customerEmail = user.email,
            paddleCustomerId = user.paddleCustomerId,
        )
    }

    fun createCreditCheckout(userId: Long, packageName: String): PaddleCheckoutData {
        val user = userRepository.findById(userId)
            ?: throw NotFoundException("사용자", userId)
        // 구독과 같은 이유다. 구매 크레딧 적립도 원장을 요구하므로 결제창 전에 막는다.
        creditService.ensureAccountPresence(userId)
        val priceId = paddleGateway.getPriceIdForCreditPackage(packageName)
            ?: throw IllegalArgumentException("해당 크레딧 패키지의 Paddle 가격 ID를 찾을 수 없습니다: $packageName")
        return PaddleCheckoutData(
            priceId = priceId,
            customData = mapOf("user_id" to userId),
            customerEmail = user.email,
            paddleCustomerId = user.paddleCustomerId,
        )
    }
}

data class PaddleConfigResponse(
    val clientToken: String,
    val environment: String,
    val paddleCustomerId: String? = null,
)

data class PaddleCheckoutData(
    val priceId: String,
    val customData: Map<String, Any>,
    val customerEmail: String,
    val paddleCustomerId: String? = null,
)
