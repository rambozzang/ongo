package com.ongo.infrastructure.security

import com.ongo.domain.auth.TokenBlacklistPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import java.sql.Timestamp
import java.time.Instant

/**
 * PostgreSQL-backed JWT revocation store.
 *
 * JWT validation remains stateless for normal requests, but an explicit logout
 * must survive a process restart and be visible to every application instance.
 * Only the short-lived JTI and expiry are stored; the access token itself is
 * never persisted.
 */
class TokenBlacklistService(
    private val jdbcTemplate: JdbcTemplate,
) : TokenBlacklistPort {

    override fun blacklist(tokenJti: String, ttlMillis: Long) {
        require(tokenJti.isNotBlank()) { "token JTI가 비어 있습니다" }
        require(ttlMillis > 0) { "토큰 TTL은 양수여야 합니다" }
        jdbcTemplate.update(
            """
            INSERT INTO revoked_jwt_tokens (jti, expires_at)
            VALUES (?, ?)
            ON CONFLICT (jti) DO UPDATE SET expires_at = EXCLUDED.expires_at
            """.trimIndent(),
            tokenJti,
            Timestamp.from(Instant.now().plusMillis(ttlMillis)),
        )
    }

    override fun isBlacklisted(tokenJti: String): Boolean {
        if (tokenJti.isBlank()) return false
        return jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM revoked_jwt_tokens WHERE jti = ? AND expires_at > CURRENT_TIMESTAMP)",
            Boolean::class.java,
            tokenJti,
        ) == true
    }

    /** 만료된 JTI는 정기적으로 제거해 테이블이 로그아웃 횟수만큼 커지지 않게 한다. */
    @Scheduled(fixedDelay = 600_000)
    fun purgeExpired() {
        jdbcTemplate.update("DELETE FROM revoked_jwt_tokens WHERE expires_at <= CURRENT_TIMESTAMP")
    }
}
