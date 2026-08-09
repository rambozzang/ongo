package com.ongo.domain.publicoauth

import java.time.LocalDateTime

/** A non-expiring Public API token. The raw token is returned only once. */
data class PublicOAuthToken(
    val id: Long? = null,
    val appId: Long,
    val userId: Long,
    val tokenPrefix: String,
    val tokenHash: String,
    val revokedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
)

interface PublicOAuthTokenRepository {
    fun findActiveByHash(tokenHash: String): PublicOAuthToken?
    fun save(token: PublicOAuthToken): PublicOAuthToken
    fun revokeByApp(appId: Long, revokedAt: LocalDateTime): Int
    fun revokeByIdAndUser(id: Long, userId: Long, revokedAt: LocalDateTime): Boolean
    fun findByUserId(userId: Long): List<PublicOAuthToken>
}
