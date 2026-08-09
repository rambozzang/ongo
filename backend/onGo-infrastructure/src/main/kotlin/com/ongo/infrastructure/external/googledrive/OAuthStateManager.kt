package com.ongo.infrastructure.external.googledrive

import com.ongo.domain.contentsource.exception.OAuthStateMismatchException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** One-time state consumption boundary. Implementations must be atomic. */
interface OAuthStateStore {
    fun consumeOnce(state: String, ttlSeconds: Long): Boolean
}

/** One-process TTL store used by the single backend instance. */
class InMemoryOAuthStateStore : OAuthStateStore {
    private val consumedStates = ConcurrentHashMap<String, Long>()

    override fun consumeOnce(state: String, ttlSeconds: Long): Boolean {
        val now = Instant.now().epochSecond
        consumedStates.entries.removeIf { now - it.value > ttlSeconds }
        return consumedStates.putIfAbsent(state, now) == null
    }
}

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
    private val stateStore: OAuthStateStore = InMemoryOAuthStateStore(),
) {
    /**
     * OAuth callback은 한 번만 처리해야 한다. state는 짧게 살지만, 같은 callback을
     * 새로고침하거나 동시에 두 번 보내면 코드 교환/연동이 중복될 수 있다.
     *
     * 운영은 단일 백엔드 인스턴스 전제로 [InMemoryOAuthStateStore]를 사용한다.
     */
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
        val now = Instant.now().epochSecond
        if (issuedAt > now || now - issuedAt > ttlSeconds) throw OAuthStateMismatchException()
        if (!consumeOnce(state)) throw OAuthStateMismatchException()
        return userId
    }

    /** 소셜 로그인용 CSRF state 발급 (userId 바인딩 없음) */
    fun issueAnonymous(): String {
        val nonce = UUID.randomUUID().toString()
        val issuedAt = Instant.now().epochSecond
        val payload = "anonymous:$nonce:$issuedAt"
        val sig = hmac(payload)
        val payloadB64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toByteArray(StandardCharsets.UTF_8))
        return "$payloadB64.$sig"
    }

    /** 소셜 로그인용 CSRF state 검증 */
    fun verifyAnonymous(state: String): Boolean {
        val parts = state.split(".")
        if (parts.size != 2) return false
        val (payloadB64, sigB64) = parts
        val payload = try {
            String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            return false
        }
        val expectedSig = hmac(payload)
        if (!MessageDigest.isEqual(
                expectedSig.toByteArray(StandardCharsets.UTF_8),
                sigB64.toByteArray(StandardCharsets.UTF_8),
            )
        ) {
            return false
        }
        val segments = payload.split(":")
        if (segments.size != 3) return false
        val (prefix, _, issuedAtStr) = segments
        if (prefix != "anonymous") return false
        val issuedAt = issuedAtStr.toLongOrNull() ?: return false
        val now = Instant.now().epochSecond
        if (issuedAt > now || now - issuedAt > ttlSeconds) return false
        return consumeOnce(state)
    }

    private fun consumeOnce(state: String): Boolean {
        return stateStore.consumeOnce(state, ttlSeconds)
    }

    private fun hmac(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8)))
    }
}
