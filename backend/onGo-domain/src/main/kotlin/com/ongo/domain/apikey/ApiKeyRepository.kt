package com.ongo.domain.apikey

import java.time.LocalDateTime

interface ApiKeyRepository {
    fun findById(id: Long): ApiKey?
    fun findByUserId(userId: Long): List<ApiKey>
    fun findActiveByHash(keyHash: String, now: LocalDateTime): ApiKey?
    fun countActiveByUserId(userId: Long): Int
    fun save(apiKey: ApiKey): ApiKey
    fun revoke(id: Long, revokedAt: LocalDateTime): Boolean
    fun touchLastUsed(id: Long, usedAt: LocalDateTime)
}
