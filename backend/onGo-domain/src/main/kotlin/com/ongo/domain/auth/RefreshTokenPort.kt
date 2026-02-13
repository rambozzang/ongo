package com.ongo.domain.auth

import java.time.LocalDateTime

/**
 * Refresh Token 저장소 Port
 * application 레이어에서 infrastructure에 의존하지 않도록 인터페이스 분리
 */
interface RefreshTokenPort {
    fun save(userId: Long, token: String, expiresAt: LocalDateTime)
    fun findByToken(token: String): RefreshTokenInfo?
    fun deleteByToken(token: String)
    fun deleteByUserId(userId: Long)
    fun deleteExpired()
}

data class RefreshTokenInfo(
    val id: Long,
    val userId: Long,
    val token: String,
    val expiresAt: LocalDateTime,
    val createdAt: LocalDateTime,
)
