package com.ongo.application.auth.dto

data class AuthResult(
    val accessToken: String,
    val refreshToken: String,
    val user: UserResult,
    val isNewUser: Boolean,
)

data class UserResult(
    val id: Long,
    val email: String,
    val name: String,
    val nickname: String?,
    val profileImageUrl: String?,
    val planType: String,
    val onboardingCompleted: Boolean,
)
