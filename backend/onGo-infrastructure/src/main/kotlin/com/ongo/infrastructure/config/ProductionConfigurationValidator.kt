package com.ongo.infrastructure.config

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
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
) {

    @PostConstruct
    fun validate() {
        require(jwtSecret.toByteArray().size >= 32) {
            "jwt.secret must contain at least 32 bytes in production"
        }

        val encryptionBytes = try {
            Base64.getDecoder().decode(platformEncryptionKey)
        } catch (_: IllegalArgumentException) {
            throw IllegalStateException("platform.token.encryption-key must be valid Base64")
        }
        require(encryptionBytes.size == 32) {
            "platform.token.encryption-key must decode to exactly 32 bytes"
        }

        val effectiveAllowedOrigins = allowedOrigins.ifBlank { appBaseUrl }
        requireReal("cors.allowed-origins (or APP_BASE_URL)", effectiveAllowedOrigins)
        require(!effectiveAllowedOrigins.split(',').any { it.trim() == "*" || it.contains("localhost") }) {
            "cors.allowed-origins must not contain '*' or localhost in production"
        }

        require(storageType.equals("s3", ignoreCase = true)) {
            "storage.type must be s3 in production"
        }
        requireReal("storage.bucket", storageBucket)
        requireReal("storage.s3.endpoint", storageEndpoint)
        require(storageEndpoint.matches(Regex("https://[a-z0-9]+\\.r2\\.cloudflarestorage\\.com/?"))) {
            "storage.s3.endpoint must be a valid Cloudflare R2 endpoint in production"
        }
        requireReal("storage.s3.access-key", storageAccessKey)
        requireReal("storage.s3.secret-key", storageSecretKey)

        requireReal("payment.portone.store-id", portoneStoreId)
        requireReal("payment.portone.channel-key", portoneChannelKey)
        requireReal("payment.portone.api-secret", portoneApiSecret)
        // A missing webhook secret makes every webhook unverifiable while the
        // service still appears healthy. Fail startup before payments can get
        // stuck in a pending state.
        requireReal("payment.portone.webhook-secret", portoneWebhookSecret)

        requireReal("google OAuth client-id", googleClientId)
        requireReal("google OAuth client-secret", googleClientSecret)
        requireReal("kakao OAuth client-id", kakaoClientId)
        requireReal("kakao OAuth client-secret", kakaoClientSecret)
        requireReal("OAUTH_STATE_SECRET", oauthStateSecret)
        require(oauthStateSecret.length >= 32) {
            "OAUTH_STATE_SECRET must contain at least 32 characters in production"
        }
        requireReal("public-api.oauth.callback-url", publicOAuthCallbackUrl)
        require(publicOAuthCallbackUrl.startsWith("https://")) {
            "public-api.oauth.callback-url must use HTTPS in production"
        }

        require(listOf(anthropicApiKey, openAiApiKey, geminiApiKey, dashScopeApiKey).any(::isRealValue)) {
            "at least one production AI provider API key must be configured"
        }
    }

    private fun requireReal(name: String, value: String) {
        require(isRealValue(value)) {
            "$name must be configured with a production value"
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
