package com.ongo.infrastructure.config

import kotlin.test.Test
import kotlin.test.assertFailsWith
import java.util.Base64
import org.springframework.mock.env.MockEnvironment

class ProductionConfigurationValidatorTest {

    @Test
    fun `운영 결제 웹훅 시크릿이 없으면 기동 검증에서 거부한다`() {
        val validator = validator(portoneWebhookSecret = "")

        val error = assertFailsWith<IllegalArgumentException> { validator.validate() }

        kotlin.test.assertTrue(error.message.orEmpty().contains("payment.portone.webhook-secret"))
    }

    /*
     * 2026-08-08 운영에서 dev 프로필이 섞여 `/auth/dev-login` 이 인증 없이 ADMIN 토큰을
     * 발급했다. 값이 모두 멀쩡해도 프로필 하나로 관리자 우회가 열리므로 기동을 거부한다.
     */
    @Test
    fun `운영에 dev 나 local 프로필이 섞이면 기동 검증에서 거부한다`() {
        for (leaked in listOf("dev", "local")) {
            val error = assertFailsWith<IllegalArgumentException>("prod+$leaked 가 기동을 통과했다") {
                validator(activeProfiles = arrayOf("prod", leaked)).validate()
            }
            kotlin.test.assertTrue(
                "development profiles must not be active in production: $leaked" in error.message.orEmpty(),
                error.message,
            )
        }
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

    /**
     * **위반을 전부 모아서 한 번에 알려준다.**
     *
     * 예전에는 `require` 23 개가 순차로 터져 첫 하나만 보였다. 값이 여러 개 빠져 있으면
     * 운영자는 **배포를 그 횟수만큼 반복**해야 전부 알 수 있었다. 이 검증기는
     * `@PostConstruct` 라 실패하면 앱이 죽고 그동안 nginx 는 502 를 내므로, 반복할수록
     * 다운타임이 곱해진다.
     */
    @Test
    fun `여러 설정이 빠지면 한 번에 모두 보고한다`() {
        val error = assertFailsWith<IllegalArgumentException> {
            validator(
                portoneWebhookSecret = "",
                portoneStoreId = "",
                openAiApiKey = "",
                anthropicApiKey = "",
                geminiApiKey = "",
                dashScopeApiKey = "",
            ).validate()
        }

        val message = error.message.orEmpty()
        listOf(
            "payment.portone.webhook-secret",
            "payment.portone.store-id",
            "spring.ai.openai.api-key",
            "at least one production AI provider",
        ).forEach { expected ->
            kotlin.test.assertTrue(
                expected in message,
                "빠진 설정 '$expected' 가 보고에 없다. 한 번에 다 보여주지 않으면 배포를 반복해야 한다:\n$message",
            )
        }
    }

    /** 몇 개가 문제인지 먼저 말해 줘야 운영자가 한 번에 고칠 분량을 가늠한다. */
    @Test
    fun `문제 개수를 먼저 알려준다`() {
        val error = assertFailsWith<IllegalArgumentException> {
            validator(portoneWebhookSecret = "", portoneStoreId = "").validate()
        }

        kotlin.test.assertTrue(
            Regex("""\d+ problem\(s\)""").containsMatchIn(error.message.orEmpty()),
            "문제 개수가 없다: ${error.message}",
        )
    }

    /**
     * 암호화 키 오타 하나가 **나머지 검사를 통째로 가리던** 자리다.
     *
     * 예전에는 Base64 디코딩 실패에서 곧바로 IllegalStateException 을 던져, 그 뒤 22 개
     * 검사가 실행되지 않았다. 운영자는 키를 고치고 다시 배포한 뒤에야 다음 문제를 봤다.
     */
    @Test
    fun `암호화 키가 깨져도 나머지 검사를 계속한다`() {
        val error = assertFailsWith<IllegalArgumentException> {
            validator(portoneStoreId = "", platformEncryptionKey = "!!not-base64!!").validate()
        }

        val message = error.message.orEmpty()
        kotlin.test.assertTrue(
            "platform.token.encryption-key" in message,
            "Base64 오류가 보고되지 않았다: $message",
        )
        kotlin.test.assertTrue(
            "payment.portone.store-id" in message,
            "Base64 오류가 뒤 검사를 가렸다 — 예전의 조기 throw 가 되돌아왔다: $message",
        )
    }

    private fun validator(
        portoneWebhookSecret: String = "d2Vic2l0ZS13ZWJob29rLXNlY3JldA==",
        portoneStoreId: String = "store-123",
        anthropicApiKey: String = "anthropic-api-key",
        openAiApiKey: String = "openai-api-key",
        geminiApiKey: String = "",
        dashScopeApiKey: String = "",
        platformEncryptionKey: String = Base64.getEncoder().encodeToString(ByteArray(32) { 1 }),
        activeProfiles: Array<String> = arrayOf("prod"),
    ) = ProductionConfigurationValidator(
        jwtSecret = "j".repeat(32),
        platformEncryptionKey = platformEncryptionKey,
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
        environment = MockEnvironment().apply { setActiveProfiles(*activeProfiles) },
    )
}
