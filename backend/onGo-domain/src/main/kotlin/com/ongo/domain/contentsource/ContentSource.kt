package com.ongo.domain.contentsource

import java.time.Instant

data class ContentSource(
    val id: Long,
    val userId: Long,
    val sourceType: ContentSourceType,
    val externalAccountId: String,
    val accountEmail: String,
    val accountDisplayName: String?,
    val accessTokenEncrypted: String,
    val refreshTokenEncrypted: String?,
    val tokenExpiresAt: Instant?,
    val grantedScopes: String?,
    val status: ContentSourceStatus,
    val lastError: String?,
    val connectedAt: Instant,
    val lastUsedAt: Instant?,
    val updatedAt: Instant,
) {
    fun needsRefresh(now: Instant): Boolean {
        val expiresAt = tokenExpiresAt ?: return true
        return expiresAt.isBefore(now.plusSeconds(REFRESH_MARGIN_SECONDS))
    }

    fun markExpired(reason: String): ContentSource =
        copy(status = ContentSourceStatus.EXPIRED, lastError = reason, updatedAt = Instant.now())

    fun markRevoked(reason: String): ContentSource =
        copy(status = ContentSourceStatus.REVOKED, lastError = reason, updatedAt = Instant.now())

    fun markUsed(): ContentSource =
        copy(lastUsedAt = Instant.now(), updatedAt = Instant.now())

    companion object {
        const val REFRESH_MARGIN_SECONDS = 60L
    }
}
