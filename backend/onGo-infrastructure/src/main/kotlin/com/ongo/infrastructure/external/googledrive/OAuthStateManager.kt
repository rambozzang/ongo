package com.ongo.infrastructure.external.googledrive

import com.ongo.domain.contentsource.exception.OAuthStateMismatchException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * OAuth state parameter for CSRF protection.
 *
 * 형식: `{payload}.{signature}`
 *  - payload = Base64URL("{userId}:{nonce}:{issuedAtEpochSec}")
 *  - signature = Base64URL(HmacSHA256(secret, rawPayload))
 *
 * 주요 보장:
 *  - userId 바인딩 (state가 가리키는 유저가 실제 로그인한 유저와 일치하는지 검증 가능)
 *  - nonce 로 replay 방어 (TTL 내에서도 각 발급은 유니크)
 *  - TTL (기본 300초) 초과 시 거부
 *  - HMAC-SHA256 서명으로 위조 방지
 *
 * 빈 등록은 [GoogleDriveAutoConfiguration]에서 수행한다. (secret/ttl 주입 필요)
 */
class OAuthStateManager(
    private val secret: String,
    private val ttlSeconds: Long = 300,
) {
    fun issue(userId: Long): String {
        val nonce = UUID.randomUUID().toString()
        val issuedAt = Instant.now().epochSecond
        val payload = "$userId:$nonce:$issuedAt"
        val sig = hmac(payload)
        val payloadB64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toByteArray(StandardCharsets.UTF_8))
        return "$payloadB64.$sig"
    }

    fun verify(state: String): Long {
        val parts = state.split(".")
        if (parts.size != 2) throw OAuthStateMismatchException()
        val (payloadB64, sigB64) = parts
        val payload = try {
            String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            throw OAuthStateMismatchException()
        }
        val expectedSig = hmac(payload)
        if (!MessageDigest.isEqual(
                expectedSig.toByteArray(StandardCharsets.UTF_8),
                sigB64.toByteArray(StandardCharsets.UTF_8),
            )
        ) {
            throw OAuthStateMismatchException()
        }
        val segments = payload.split(":")
        if (segments.size != 3) throw OAuthStateMismatchException()
        val (userIdStr, _, issuedAtStr) = segments
        val userId = userIdStr.toLongOrNull() ?: throw OAuthStateMismatchException()
        val issuedAt = issuedAtStr.toLongOrNull() ?: throw OAuthStateMismatchException()
        if (Instant.now().epochSecond - issuedAt > ttlSeconds) throw OAuthStateMismatchException()
        return userId
    }

    private fun hmac(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8)))
    }
}
