package com.ongo.api.auth.dto

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserResponse,
    val isNewUser: Boolean,
)

data class UserResponse(
    val id: Long,
    val email: String,
    val name: String,
    val nickname: String?,
    val profileImageUrl: String?,
    val planType: String,
    val onboardingCompleted: Boolean,
)
