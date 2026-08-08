package com.ongo.application.apikey.dto

import java.time.LocalDateTime

data class CreateApiKeyRequest(
    val name: String,
    val expiresAt: LocalDateTime? = null,
)

data class ApiKeyResponse(
    val id: Long,
    val name: String,
    val keyPrefix: String,
    /** Only populated in the create response. */
    val token: String? = null,
    val lastUsedAt: LocalDateTime?,
    val expiresAt: LocalDateTime?,
    val revokedAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
)
