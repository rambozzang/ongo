package com.ongo.infrastructure.security.oauth

import com.fasterxml.jackson.annotation.JsonProperty
import com.ongo.common.exception.UnauthorizedException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

@Service
class KakaoOAuth2Service(
    @Value("\${spring.security.oauth2.client.registration.kakao.client-id}") private val clientId: String,
    @Value("\${spring.security.oauth2.client.registration.kakao.client-secret}") private val clientSecret: String,
) {
    private val restClient = RestClient.create()

    fun getUserInfo(code: String, redirectUri: String): OAuth2UserInfo {
        val tokenResponse = exchangeCodeForToken(code, redirectUri)
        return fetchUserInfo(tokenResponse)
    }

    private fun exchangeCodeForToken(code: String, redirectUri: String): KakaoTokenResponse {
        val params = LinkedMultiValueMap<String, String>()
        params.add("grant_type", "authorization_code")
        params.add("client_id", clientId)
        params.add("client_secret", clientSecret)
        params.add("redirect_uri", redirectUri)
        params.add("code", code)

        return restClient.post()
            .uri("https://kauth.kakao.com/oauth/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(params)
            .retrieve()
            .body(KakaoTokenResponse::class.java)
            ?: throw UnauthorizedException("Kakao 토큰 발급에 실패했습니다")
    }

    @Suppress("UNCHECKED_CAST")
    private fun fetchUserInfo(tokenResponse: KakaoTokenResponse): OAuth2UserInfo {
        val userInfo = restClient.get()
            .uri("https://kapi.kakao.com/v2/user/me")
            .header("Authorization", "Bearer ${tokenResponse.accessToken}")
            .retrieve()
            .body(Map::class.java)
            ?: throw UnauthorizedException("Kakao 사용자 정보를 가져올 수 없습니다")

        val id = userInfo["id"].toString()
        val kakaoAccount = userInfo["kakao_account"] as? Map<String, Any> ?: emptyMap()
        val profile = kakaoAccount["profile"] as? Map<String, Any> ?: emptyMap()

        val email = kakaoAccount["email"] as? String ?: ""
        val nickname = profile["nickname"] as? String ?: "카카오 사용자"
        val profileImageUrl = profile["profile_image_url"] as? String

        return OAuth2UserInfo(
            providerId = id,
            email = email,
            name = nickname,
            profileImageUrl = profileImageUrl,
        )
    }

    data class KakaoTokenResponse(
        @JsonProperty("access_token") val accessToken: String = "",
        @JsonProperty("token_type") val tokenType: String = "",
        @JsonProperty("refresh_token") val refreshToken: String? = null,
        @JsonProperty("expires_in") val expiresIn: Int = 0,
        val scope: String? = null,
    )
}
