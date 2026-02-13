package com.ongo.infrastructure.security.oauth

import com.fasterxml.jackson.annotation.JsonProperty
import com.ongo.common.exception.UnauthorizedException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

@Service
class GoogleOAuth2Service(
    @Value("\${spring.security.oauth2.client.registration.google.client-id}") private val clientId: String,
    @Value("\${spring.security.oauth2.client.registration.google.client-secret}") private val clientSecret: String,
) {
    private val restClient = RestClient.create()

    fun getUserInfo(code: String, redirectUri: String): OAuth2UserInfo {
        val tokenResponse = exchangeCodeForToken(code, redirectUri)
        return fetchUserInfo(tokenResponse)
    }

    private fun exchangeCodeForToken(code: String, redirectUri: String): GoogleTokenResponse {
        val params = LinkedMultiValueMap<String, String>()
        params.add("code", code)
        params.add("client_id", clientId)
        params.add("client_secret", clientSecret)
        params.add("redirect_uri", redirectUri)
        params.add("grant_type", "authorization_code")

        return restClient.post()
            .uri("https://oauth2.googleapis.com/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(params)
            .retrieve()
            .body(GoogleTokenResponse::class.java)
            ?: throw UnauthorizedException("Google 토큰 발급에 실패했습니다")
    }

    private fun fetchUserInfo(tokenResponse: GoogleTokenResponse): OAuth2UserInfo {
        val userInfo = restClient.get()
            .uri("https://www.googleapis.com/oauth2/v2/userinfo")
            .header("Authorization", "Bearer ${tokenResponse.accessToken}")
            .retrieve()
            .body(GoogleUserInfo::class.java)
            ?: throw UnauthorizedException("Google 사용자 정보를 가져올 수 없습니다")

        return OAuth2UserInfo(
            providerId = userInfo.id,
            email = userInfo.email,
            name = userInfo.name,
            profileImageUrl = userInfo.picture,
        )
    }

    data class GoogleTokenResponse(
        @JsonProperty("access_token") val accessToken: String = "",
        @JsonProperty("token_type") val tokenType: String = "",
        @JsonProperty("expires_in") val expiresIn: Int = 0,
        val scope: String = "",
        @JsonProperty("id_token") val idToken: String? = null,
    )

    data class GoogleUserInfo(
        val id: String = "",
        val email: String = "",
        val name: String = "",
        val picture: String? = null,
    )
}
