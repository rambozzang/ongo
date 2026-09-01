package com.ongo.application.channel

import com.ongo.common.enums.Platform
import com.ongo.common.exception.BusinessException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Signs the state used by the authenticated channel OAuth flow.
 *
 * The browser may supply a UI context, but it must never be the authority for
 * which account is allowed to attach the returned authorization code. The
 * signed payload binds the state to the logged-in user, platform and exact
 * redirect URI. A short-lived one-time store also prevents a callback refresh
 * from being processed twice.
 */
@Service
class ChannelOAuthStateManager(
    @param:Value("\${ongo.content-source.google-drive.oauth-state-secret:}") private val stateSecret: String,
    @param:Value("\${channel.oauth.state-ttl-seconds:600}") private val ttlSeconds: Long = DEFAULT_TTL_SECONDS,
) {
    private val consumedStates = ConcurrentHashMap<String, Long>()
    private val secureRandom = SecureRandom()

    fun issue(userId: Long, platform: Platform, redirectUri: String, clientState: String): String {
        requireStateSecret()
        require(userId > 0) { "OAuth userId가 올바르지 않습니다" }
        require(clientState.isNotBlank() && clientState.length <= MAX_CLIENT_STATE_LENGTH) {
            "OAuth state가 올바르지 않습니다"
        }

        val issuedAt = Instant.now().epochSecond
        val rawPayload = listOf(
            VERSION,
            userId.toString(),
            platform.name,
            encode(redirectUri.toByteArray(StandardCharsets.UTF_8)),
            encode(clientState.toByteArray(StandardCharsets.UTF_8)),
            issuedAt.toString(),
            randomNonce(),
        ).joinToString(".")
        val encodedPayload = encode(rawPayload.toByteArray(StandardCharsets.UTF_8))
        return "$encodedPayload.${encode(sign(encodedPayload.toByteArray(StandardCharsets.US_ASCII)))}"
    }

    /**
     * Validates and consumes a callback state before an authorization code is
     * exchanged. Invalid states intentionally expose one generic error so the
     * endpoint does not become a state oracle.
     */
    fun verifyAndConsume(
        state: String,
        expectedUserId: Long,
        expectedPlatform: Platform,
        expectedRedirectUri: String,
    ) {
        // Do not disclose a deployment-secret problem through a distinct
        // callback error. Authorization URL creation still reports the
        // configuration error so operators can fix it; callbacks fail closed.
        if (stateSecret.length < MIN_SECRET_LENGTH) invalid()
        if (state.length > MAX_STATE_LENGTH) invalid()
        val parts = state.split('.', limit = 2)
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) invalid()

        val encodedPayload = parts[0]
        val signature = parts[1]
        val actualSignature = runCatching { Base64.getUrlDecoder().decode(signature) }.getOrNull() ?: invalid()
        val expectedSignature = sign(encodedPayload.toByteArray(StandardCharsets.US_ASCII))
        if (!MessageDigest.isEqual(actualSignature, expectedSignature)) invalid()

        val payload = decode(encodedPayload) ?: invalid()
        val values = payload.split('.')
        if (values.size != PAYLOAD_PARTS || values[0] != VERSION) invalid()
        val userId = values[1].toLongOrNull() ?: invalid()
        val platform = runCatching { Platform.valueOf(values[2]) }.getOrNull() ?: invalid()
        val redirectUri = decode(values[3]) ?: invalid()
        // The UI context is included in the signed payload even though it is
        // not used for authorization. Any client-side change also invalidates
        // the signature, while the server never treats this context as authz.
        if (decode(values[4]) == null) invalid()
        val issuedAt = values[5].toLongOrNull() ?: invalid()
        val now = Instant.now().epochSecond
        if (issuedAt > now || now - issuedAt > ttlSeconds) invalid()
        if (userId != expectedUserId || platform != expectedPlatform || redirectUri != expectedRedirectUri) invalid()

        cleanup(now)
        if (consumedStates.putIfAbsent(state, now) != null) invalid()
    }

    private fun cleanup(now: Long) {
        consumedStates.entries.removeIf { now - it.value > ttlSeconds }
    }

    private fun randomNonce(): String {
        val bytes = ByteArray(NONCE_BYTES)
        secureRandom.nextBytes(bytes)
        return encode(bytes)
    }

    private fun decode(value: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
    }.getOrNull()

    private fun sign(value: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(stateSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        doFinal(value)
    }

    private fun encode(value: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value)

    private fun requireStateSecret() {
        if (stateSecret.length < MIN_SECRET_LENGTH) {
            throw BusinessException("OAUTH_STATE_NOT_CONFIGURED", "OAuth state secret이 설정되지 않았습니다")
        }
    }

    private fun invalid(): Nothing = throw BusinessException("OAUTH_STATE_INVALID", "OAuth state 검증에 실패했습니다")

    companion object {
        private const val VERSION = "channel-v1"
        private const val PAYLOAD_PARTS = 7
        private const val NONCE_BYTES = 32
        private const val MIN_SECRET_LENGTH = 32
        private const val MAX_CLIENT_STATE_LENGTH = 1024
        private const val MAX_STATE_LENGTH = 4096
        private const val DEFAULT_TTL_SECONDS = 600L
    }
}
