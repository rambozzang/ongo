package com.ongo.domain.auth

import com.ongo.common.enums.AuthProvider

interface OAuth2Port {
    fun getUserInfo(provider: AuthProvider, code: String, redirectUri: String): OAuth2UserResult
    fun resolveProvider(provider: String): AuthProvider
}

data class OAuth2UserResult(
    val providerId: String,
    val email: String,
    val name: String,
    val profileImageUrl: String?,
)
