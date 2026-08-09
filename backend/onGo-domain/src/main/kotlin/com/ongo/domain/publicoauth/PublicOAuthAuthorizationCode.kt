package com.ongo.domain.publicoauth

import java.time.LocalDateTime

/** Short-lived, single-use authorization code. The raw code is never persisted. */
data class PublicOAuthAuthorizationCode(
    val id: Long? = null,
    val appId: Long,
    val userId: Long,
    val codeHash: String,
    val redirectUri: String,
    val state: String? = null,
    val expiresAt: LocalDateTime,
    val consumedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
)

interface PublicOAuthAuthorizationCodeRepository {
    fun save(code: PublicOAuthAuthorizationCode): PublicOAuthAuthorizationCode

    /** Atomically consumes a still-valid code, returning null for replay/expiry. */
    fun consume(codeHash: String, now: LocalDateTime): PublicOAuthAuthorizationCode?
}
