package com.ongo.infrastructure.config

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.core.env.StandardEnvironment
import org.springframework.stereotype.Component
import com.ongo.common.config.DevOnlyProfiles
import java.util.Base64

/**
 * Prevents a deployment with development fallbacks from looking healthy.
 *
 * Production configuration is intentionally validated at startup: accepting a
 * request and failing later during OAuth, payment, or storage operations is
 * much harder to detect and can leave a customer with an incomplete workflow.
 */
@Profile("prod")
@Component
class ProductionConfigurationValidator(
    @Value("\${jwt.secret:}") private val jwtSecret: String,
    @Value("\${platform.token.encryption-key:}") private val platformEncryptionKey: String,
    @Value("\${cors.allowed-origins:}") private val allowedOrigins: String,
    @Value("\${APP_BASE_URL:}") private val appBaseUrl: String,
    @Value("\${storage.type:}") private val storageType: String,
    @Value("\${storage.bucket:}") private val storageBucket: String,
    @Value("\${storage.s3.endpoint:}") private val storageEndpoint: String,
    @Value("\${storage.s3.access-key:}") private val storageAccessKey: String,
    @Value("\${storage.s3.secret-key:}") private val storageSecretKey: String,
    @Value("\${payment.portone.store-id:}") private val portoneStoreId: String,
    @Value("\${payment.portone.channel-key:}") private val portoneChannelKey: String,
    @Value("\${payment.portone.api-secret:}") private val portoneApiSecret: String,
    @Value("\${payment.portone.webhook-secret:}") private val portoneWebhookSecret: String,
    @Value("\${spring.security.oauth2.client.registration.google.client-id:}") private val googleClientId: String,
    @Value("\${spring.security.oauth2.client.registration.google.client-secret:}") private val googleClientSecret: String,
    @Value("\${spring.security.oauth2.client.registration.kakao.client-id:}") private val kakaoClientId: String,
    @Value("\${spring.security.oauth2.client.registration.kakao.client-secret:}") private val kakaoClientSecret: String,
    @Value("\${ongo.content-source.google-drive.oauth-state-secret:}") private val oauthStateSecret: String,
    @Value("\${public-api.oauth.callback-url:}") private val publicOAuthCallbackUrl: String,
    @Value("\${spring.ai.anthropic.api-key:}") private val anthropicApiKey: String,
    @Value("\${spring.ai.openai.api-key:}") private val openAiApiKey: String,
    @Value("\${spring.ai.google.genai.api-key:}") private val geminiApiKey: String,
    @Value("\${dashscope.api-key:}") private val dashScopeApiKey: String,
    private val environment: Environment = StandardEnvironment(),
) {

    /**
     * **위반을 전부 모아 한 번에 보고한다.**
     *
     * 예전에는 첫 `require` 에서 멈춰, 값이 다섯 개 빠져 있으면 배포를 다섯 번 반복해야
     * 전부 알 수 있었다. 이 검증기는 `@PostConstruct` 라 실패하면 앱이 죽고 그 사이
     * nginx 는 502 를 낸다 — 반복할수록 다운타임이 곱해진다.
     *
     * 검사 자체는 그대로다. fail-closed 도 그대로다 — 하나라도 위반이면 기동하지 않는다.
     */
    @PostConstruct
    fun validate() {
        val violations = Violations()

        /*
         * **개발 프로필이 운영에 섞이면 기동하지 않는다.**
         *
         * dev/local 이 켜지면 인증 없는 관리자 로그인 같은 개발 전용 빈이 뜬다. 컨트롤러 쪽도
         * `DevOnlyProfiles.EXPRESSION` 으로 prod 와 함께면 꺼지지만, 그 한 겹만 믿지 않는다 —
         * 개발용 기본값(application-dev.yml)이 운영 값을 덮는 것까지 막으려면 기동 자체를 거부해야 한다.
         */
        val leakedProfiles = environment.activeProfiles.filter { it in DevOnlyProfiles.FORBIDDEN_WITH_PROD }
        violations.check(leakedProfiles.isEmpty()) {
            "development profiles must not be active in production: ${leakedProfiles.joinToString()}"
        }

        violations.check(jwtSecret.toByteArray().size >= 32) {
            "jwt.secret must contain at least 32 bytes in production"
        }

        /*
         * Base64 디코딩 실패는 **던지지 않고 담는다.**
         *
         * 예전에는 여기서 곧바로 IllegalStateException 을 던져, 그 뒤의 검사가 전부
         * 실행되지 않았다. 암호화 키 오타 하나가 나머지 22 개 문제를 가리던 자리다.
         */
        val encryptionBytes = try {
            Base64.getDecoder().decode(platformEncryptionKey)
        } catch (_: IllegalArgumentException) {
            null
        }
        if (encryptionBytes == null) {
            violations.check(false) { "platform.token.encryption-key must be valid Base64" }
        } else {
            violations.check(encryptionBytes.size == 32) {
                "platform.token.encryption-key must decode to exactly 32 bytes"
            }
        }

        val effectiveAllowedOrigins = allowedOrigins.ifBlank { appBaseUrl }
        violations.requireReal("cors.allowed-origins (or APP_BASE_URL)", effectiveAllowedOrigins, ::isRealValue)
        violations.check(!effectiveAllowedOrigins.split(',').any { it.trim() == "*" || it.contains("localhost") }) {
            "cors.allowed-origins must not contain '*' or localhost in production"
        }

        violations.check(storageType.equals("s3", ignoreCase = true)) {
            "storage.type must be s3 in production"
        }
        violations.requireReal("storage.bucket", storageBucket, ::isRealValue)
        violations.requireReal("storage.s3.endpoint", storageEndpoint, ::isRealValue)
        violations.check(storageEndpoint.matches(Regex("https://[a-z0-9]+\\.r2\\.cloudflarestorage\\.com/?"))) {
            "storage.s3.endpoint must be a valid Cloudflare R2 endpoint in production"
        }
        violations.requireReal("storage.s3.access-key", storageAccessKey, ::isRealValue)
        violations.requireReal("storage.s3.secret-key", storageSecretKey, ::isRealValue)

        violations.requireReal("payment.portone.store-id", portoneStoreId, ::isRealValue)
        violations.requireReal("payment.portone.channel-key", portoneChannelKey, ::isRealValue)
        violations.requireReal("payment.portone.api-secret", portoneApiSecret, ::isRealValue)
        // A missing webhook secret makes every webhook unverifiable while the
        // service still appears healthy. Fail startup before payments can get
        // stuck in a pending state.
        violations.requireReal("payment.portone.webhook-secret", portoneWebhookSecret, ::isRealValue)

        violations.requireReal("google OAuth client-id", googleClientId, ::isRealValue)
        violations.requireReal("google OAuth client-secret", googleClientSecret, ::isRealValue)
        violations.requireReal("kakao OAuth client-id", kakaoClientId, ::isRealValue)
        violations.requireReal("kakao OAuth client-secret", kakaoClientSecret, ::isRealValue)
        violations.requireReal("OAUTH_STATE_SECRET", oauthStateSecret, ::isRealValue)
        violations.check(oauthStateSecret.length >= 32) {
            "OAUTH_STATE_SECRET must contain at least 32 characters in production"
        }
        violations.requireReal("public-api.oauth.callback-url", publicOAuthCallbackUrl, ::isRealValue)
        violations.check(publicOAuthCallbackUrl.startsWith("https://")) {
            "public-api.oauth.callback-url must use HTTPS in production"
        }

        violations.check(listOf(anthropicApiKey, openAiApiKey, geminiApiKey, dashScopeApiKey).any(::isRealValue)) {
            "at least one production AI provider API key must be configured"
        }

        /*
         * OpenAI is not interchangeable with the other providers.
         *
         * The Shorts pipeline's first stage transcribes through
         * OpenAiAudioTranscriptionModel specifically, so the "any one provider"
         * rule above can be satisfied by DashScope alone while every pipeline
         * run still dies at TRANSCRIBE with a provider auth error. That failure
         * surfaces minutes into a paid run, long after the user committed.
         */
        violations.requireReal("spring.ai.openai.api-key (Shorts transcription)", openAiApiKey, ::isRealValue)

        violations.throwIfAny()
    }

    /**
     * 위반을 **모아 두는** 수집기. 첫 실패에서 멈추지 않는다.
     *
     * ## 왜 모으는가
     *
     * 예전에는 `require` 23 개가 순차로 터졌다. 값이 다섯 개 빠져 있으면 운영자는
     * **배포를 다섯 번 반복**해야 전부 알 수 있었다. 한 번에 하나씩, 매번 몇 분씩.
     *
     * 게다가 이 검증기는 `@PostConstruct` 라 실패하면 앱이 죽는다. 그 사이 nginx 는
     * 502 를 낸다. 한 번에 다 보여주면 한 번의 수정으로 끝난다.
     */
    private class Violations {
        private val messages = mutableListOf<String>()

        fun check(condition: Boolean, message: () -> String) {
            if (!condition) messages += message()
        }

        fun requireReal(name: String, value: String, isReal: (String) -> Boolean) {
            check(isReal(value)) { "$name must be configured with a production value" }
        }

        /**
         * 하나라도 있으면 전부 담아 던진다.
         *
         * 번호를 붙이는 이유는 journal 에서 줄바꿈이 뭉개져도 항목 경계를 알아볼 수
         * 있게 하기 위해서다.
         */
        fun throwIfAny() {
            if (messages.isEmpty()) return
            // `require` 가 던지던 타입을 그대로 쓴다. 설정 **값**이 잘못된 것이므로
            // IllegalArgumentException 이 맞고, 기존 호출부·테스트의 계약도 유지된다.
            // (Base64 실패만 IllegalStateException 이었는데 그 불일치도 여기서 없어진다.)
            throw IllegalArgumentException(
                buildString {
                    append("production configuration is incomplete (")
                    append(messages.size)
                    append(" problem(s)):")
                    messages.forEachIndexed { index, message ->
                        append("\n  ${index + 1}. $message")
                    }
                    append("\n운영 .env 를 고친 뒤 다시 배포하세요. 값 자체는 출력하지 않습니다.")
                },
            )
        }
    }

    private fun isRealValue(value: String): Boolean {
        val normalized = value.trim().lowercase()
        if (normalized.length < MIN_REAL_VALUE_LENGTH) return false
        return listOf("dummy", "placeholder", "change-me", "your-", "localhost").none(normalized::contains)
    }

    companion object {
        // A two-character value can pass a non-empty check while still being an
        // obvious placeholder (the production .env had exactly this failure).
        // This is intentionally a conservative sanity check, not provider
        // validation; provider-specific formats remain the provider's concern.
        private const val MIN_REAL_VALUE_LENGTH = 8
    }
}
