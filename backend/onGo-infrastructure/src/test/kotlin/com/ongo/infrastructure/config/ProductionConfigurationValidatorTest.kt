package com.ongo.infrastructure.config

import kotlin.test.Test
import kotlin.test.assertFailsWith
import java.util.Base64

class ProductionConfigurationValidatorTest {

    @Test
    fun `운영 결제 웹훅 시크릿이 없으면 기동 검증에서 거부한다`() {
        val validator = validator(portoneWebhookSecret = "")

        val error = assertFailsWith<IllegalArgumentException> { validator.validate() }

        kotlin.test.assertTrue(error.message.orEmpty().contains("payment.portone.webhook-secret"))
    }

    @Test
    fun `운영 필수 설정이 모두 있으면 기동 검증을 통과한다`() {
        validator().validate()
    }

    @Test
    fun `짧은 placeholder 값은 운영 기동 검증에서 거부한다`() {
        val error = assertFailsWith<IllegalArgumentException> {
            validator(portoneStoreId = "12").validate()
        }

        kotlin.test.assertTrue(error.message.orEmpty().contains("payment.portone.store-id"))
    }

    @Test
    fun `Gemini 키만 설정된 운영 환경도 AI 기동 검증을 통과한다`() {
        validator(
            anthropicApiKey = "",
            openAiApiKey = "",
            geminiApiKey = "gemini-api-key",
            dashScopeApiKey = "",
        ).validate()
    }

    private fun validator(
        portoneWebhookSecret: String = "d2Vic2l0ZS13ZWJob29rLXNlY3JldA==",
        portoneStoreId: String = "store-123",
        anthropicApiKey: String = "anthropic-api-key",
        openAiApiKey: String = "",
        geminiApiKey: String = "",
        dashScopeApiKey: String = "",
    ) = ProductionConfigurationValidator(
        jwtSecret = "j".repeat(32),
        platformEncryptionKey = Base64.getEncoder().encodeToString(ByteArray(32) { 1 }),
        allowedOrigins = "https://ongo.test",
        appBaseUrl = "https://ongo.test",
        storageType = "s3",
        storageBucket = "ongo-videos",
        storageEndpoint = "https://abc123.r2.cloudflarestorage.com",
        storageAccessKey = "r2-access-key",
        storageSecretKey = "r2-secret-key",
        portoneStoreId = portoneStoreId,
        portoneChannelKey = "channel-key-123",
        portoneApiSecret = "portone-api-secret",
        portoneWebhookSecret = portoneWebhookSecret,
        googleClientId = "google-client-id",
        googleClientSecret = "google-client-secret",
        kakaoClientId = "kakao-client-id",
        kakaoClientSecret = "kakao-client-secret",
        oauthStateSecret = "o".repeat(32),
        publicOAuthCallbackUrl = "https://ongo.test/oauth/callback",
        anthropicApiKey = anthropicApiKey,
        openAiApiKey = openAiApiKey,
        geminiApiKey = geminiApiKey,
        dashScopeApiKey = dashScopeApiKey,
    )
}
