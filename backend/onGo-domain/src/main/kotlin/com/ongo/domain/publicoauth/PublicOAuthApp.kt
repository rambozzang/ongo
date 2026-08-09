package com.ongo.domain.publicoauth

import java.time.LocalDateTime

/** A third-party developer application allowed to request Public API access. */
data class PublicOAuthApp(
    val id: Long? = null,
    val ownerId: Long,
    val clientId: String,
    val clientSecretHash: String,
    val name: String,
    val description: String? = null,
    val profilePictureUrl: String? = null,
    val redirectUri: String,
    val revokedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)

interface PublicOAuthAppRepository {
    fun findById(id: Long): PublicOAuthApp?
    fun findByClientId(clientId: String): PublicOAuthApp?
    fun findByOwnerId(ownerId: Long): List<PublicOAuthApp>
    fun save(app: PublicOAuthApp): PublicOAuthApp
    fun update(app: PublicOAuthApp): PublicOAuthApp
    fun rotateSecret(id: Long, secretHash: String, updatedAt: LocalDateTime): Boolean
    fun revoke(id: Long, ownerId: Long, revokedAt: LocalDateTime): Boolean
}
