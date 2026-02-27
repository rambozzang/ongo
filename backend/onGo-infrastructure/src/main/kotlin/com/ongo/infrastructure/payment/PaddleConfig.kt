package com.ongo.infrastructure.payment

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "payment.paddle")
data class PaddleConfig(
    val apiKey: String = "",
    val webhookSecret: String = "",
    val clientToken: String = "",
    val environment: String = "sandbox",
    val price: PriceConfig = PriceConfig(),
) {
    data class PriceConfig(
        val starter: String = "",
        val pro: String = "",
        val business: String = "",
        val creditStarter: String = "",
        val creditBasic: String = "",
        val creditPro: String = "",
        val creditBusiness: String = "",
    )
}
