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
    fun `일반 AI 기능은 Gemini 키만으로도 기동 검증을 통과한다`() {
        validator(
            anthropicApiKey = "",
            geminiApiKey = "gemini-api-key",
            dashScopeApiKey = "",
        ).validate()
    }

    /*
     * 쇼츠 TRANSCRIBE 는 OpenAiAudioTranscriptionModel 로 고정돼 있다.
     * "AI 키 1개 이상" 규칙은 DashScope 하나로도 만족되므로, 그 규칙만으로는
     * 전사 키가 빠진 배포가 정상 기동한 뒤 모든 실행이 1단계에서 죽는다.
     */
    @Test
    fun `DashScope 만 있고 OpenAI 키가 없으면 기동 검증에서 거부한다`() {
        val error = assertFailsWith<IllegalArgumentException> {
            validator(
                anthropicApiKey = "",
                openAiApiKey = "",
                geminiApiKey = "",
                dashScopeApiKey = "dashscope-api-key",
            ).validate()
        }

        kotlin.test.assertTrue(error.message.orEmpty().contains("spring.ai.openai.api-key"))
    }

    @Test
    fun `OpenAI 키가 dummy placeholder 면 기동 검증에서 거부한다`() {
        val error = assertFailsWith<IllegalArgumentException> {
            validator(openAiApiKey = "dummy-openai-key").validate()
        }

        kotlin.test.assertTrue(error.message.orEmpty().contains("spring.ai.openai.api-key"))
    }

    private fun validator(
        portoneWebhookSecret: String = "d2Vic2l0ZS13ZWJob29rLXNlY3JldA==",
        portoneStoreId: String = "store-123",
        anthropicApiKey: String = "anthropic-api-key",
        openAiApiKey: String = "openai-api-key",
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
