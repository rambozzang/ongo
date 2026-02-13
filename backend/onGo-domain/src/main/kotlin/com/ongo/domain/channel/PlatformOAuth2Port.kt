package com.ongo.domain.channel

import com.ongo.common.enums.Platform

/**
 * Domain port interface for platform-specific OAuth2 token exchange.
 * Used when connecting channels (exchanging authorization codes for tokens).
 * Implemented by infrastructure layer (OAuth2ServiceFactory adapter).
 */
interface PlatformOAuth2Port {
    fun exchangeCodeForTokens(platform: Platform, authorizationCode: String, redirectUri: String): PlatformOAuth2TokenResult
}

data class PlatformOAuth2TokenResult(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Long,
)
