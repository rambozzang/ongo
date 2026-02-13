package com.ongo.infrastructure.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import com.ongo.domain.auth.AuthTokenPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.access-token-expiry}") private val accessTokenExpiry: Long,
    @Value("\${jwt.refresh-token-expiry}") private val refreshTokenExpiry: Long,
) : AuthTokenPort {
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(secret.toByteArray())
    }

    override fun generateAccessToken(userId: Long): String {
        return generateToken(userId, accessTokenExpiry, "access")
    }

    override fun generateRefreshToken(userId: Long): String {
        return generateToken(userId, refreshTokenExpiry, "refresh")
    }

    private fun generateToken(userId: Long, expiry: Long, type: String): String {
        val now = Date()
        val expiryDate = Date(now.time + expiry)

        return Jwts.builder()
            .subject(userId.toString())
            .claim("type", type)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)
            .compact()
    }

    override fun validateToken(token: String): Boolean {
        return try {
            val claims = parseClaims(token)
            !claims.expiration.before(Date())
        } catch (e: ExpiredJwtException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    override fun getUserIdFromToken(token: String): Long {
        val claims = parseClaims(token)
        return claims.subject.toLong()
    }

    override fun getTokenType(token: String): String {
        val claims = parseClaims(token)
        return claims["type"] as String
    }

    override fun getRefreshTokenExpiryMillis(): Long = refreshTokenExpiry

    private fun parseClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}
