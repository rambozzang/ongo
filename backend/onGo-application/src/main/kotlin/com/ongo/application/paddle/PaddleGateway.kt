package com.ongo.application.paddle

/**
 * Paddle API 호출을 위한 포트 인터페이스
 * - application 레이어에서 infrastructure(PaddleClient) 직접 의존 방지
 * - infrastructure 모듈의 PaddleGatewayImpl이 구현
 */
interface PaddleGateway {
    fun updateSubscription(subscriptionId: String, newPriceId: String, prorationMode: String): Any
    fun cancelSubscription(subscriptionId: String, effectiveFrom: String): Any
    fun getPriceIdForPlan(planType: String): String?
    fun getPriceIdForCreditPackage(packageName: String): String?
    fun verifyWebhookSignature(rawBody: String, paddleSignature: String): Boolean
    fun getTransactionInvoice(transactionId: String): String?
    fun getClientToken(): String
    fun getEnvironment(): String
}
