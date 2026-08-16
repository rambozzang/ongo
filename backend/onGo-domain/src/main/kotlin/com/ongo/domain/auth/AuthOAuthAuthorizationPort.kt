package com.ongo.domain.auth

/** Builds a social-login authorization URL from server-owned credentials. */
interface AuthOAuthAuthorizationPort {
    fun buildAuthorizationUrl(
        provider: AuthOAuthProvider,
        redirectUri: String,
        state: String,
    ): String
}

enum class AuthOAuthProvider {
    GOOGLE,
    KAKAO,
}
