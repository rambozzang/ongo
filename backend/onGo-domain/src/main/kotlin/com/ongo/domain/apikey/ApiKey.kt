package com.ongo.domain.apikey

import java.time.LocalDateTime

/** A personal automation credential. The secret itself is never persisted. */
data class ApiKey(
    val id: Long? = null,
    val userId: Long,
    val name: String,
    val keyPrefix: String,
    val keyHash: String,
    val lastUsedAt: LocalDateTime? = null,
    val expiresAt: LocalDateTime? = null,
    val revokedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
)
