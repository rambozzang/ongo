package com.ongo.infrastructure.payment

import com.ongo.application.paddle.PaddleGateway
import org.springframework.stereotype.Component

@Component
class PaddleGatewayImpl(
    private val paddleClient: PaddleClient,
    private val paddleConfig: PaddleConfig,
) : PaddleGateway {

    override fun updateSubscription(subscriptionId: String, newPriceId: String, prorationMode: String): Any =
        paddleClient.updateSubscription(subscriptionId, newPriceId, prorationMode)

    override fun cancelSubscription(subscriptionId: String, effectiveFrom: String): Any =
        paddleClient.cancelSubscription(subscriptionId, effectiveFrom)

    override fun getPriceIdForPlan(planType: String): String? =
        paddleClient.getPriceIdForPlan(planType)

    override fun getPriceIdForCreditPackage(packageName: String): String? =
        paddleClient.getPriceIdForCreditPackage(packageName)

    override fun verifyWebhookSignature(rawBody: String, paddleSignature: String): Boolean =
        paddleClient.verifyWebhookSignature(rawBody, paddleSignature)

    override fun getTransactionInvoice(transactionId: String): String? =
        paddleClient.getTransactionInvoice(transactionId)

    override fun getClientToken(): String = paddleConfig.clientToken

    override fun getEnvironment(): String = paddleConfig.environment
}
