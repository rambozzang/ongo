package com.ongo.application.paddle

import com.ongo.common.exception.NotFoundException
import com.ongo.domain.user.UserRepository
import org.springframework.stereotype.Service

@Service
class PaddleCheckoutService(
    private val paddleGateway: PaddleGateway,
    private val userRepository: UserRepository,
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

    fun createSubscriptionCheckout(userId: Long, planType: String): PaddleCheckoutData {
        val user = userRepository.findById(userId)
            ?: throw NotFoundException("사용자", userId)
        val priceId = paddleGateway.getPriceIdForPlan(planType)
            ?: throw IllegalArgumentException("해당 플랜의 Paddle 가격 ID를 찾을 수 없습니다: $planType")
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
