package com.ongo.infrastructure.external.googledrive.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Google OAuth2 token endpoint 응답 DTO.
 *
 * - `accessToken`: Bearer 토큰 (짧은 수명)
 * - `refreshToken`: 갱신용 토큰 (최초 교환 또는 `prompt=consent`로 재발급 시에만 반환)
 * - `expiresIn`: access token 만료까지 남은 초
 * - `idToken`: OpenID 스코프 요청 시 포함
 * - `scope`: 실제 승인된 스코프 (공백 구분)
 */
data class TokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("refresh_token") val refreshToken: String? = null,
    @JsonProperty("expires_in") val expiresIn: Int,
    @JsonProperty("id_token") val idToken: String? = null,
    @JsonProperty("scope") val scope: String? = null,
    @JsonProperty("token_type") val tokenType: String = "Bearer",
)
