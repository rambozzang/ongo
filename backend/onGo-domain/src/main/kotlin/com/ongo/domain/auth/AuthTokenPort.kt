package com.ongo.domain.auth

interface AuthTokenPort {
    fun generateAccessToken(userId: Long): String
    fun generateRefreshToken(userId: Long): String
    fun validateToken(token: String): Boolean
    fun getUserIdFromToken(token: String): Long
    fun getTokenType(token: String): String
    fun getRefreshTokenExpiryMillis(): Long
}
