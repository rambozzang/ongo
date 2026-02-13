package com.ongo.infrastructure.security.oauth

data class OAuth2UserInfo(
    val providerId: String,
    val email: String,
    val name: String,
    val profileImageUrl: String?,
)
