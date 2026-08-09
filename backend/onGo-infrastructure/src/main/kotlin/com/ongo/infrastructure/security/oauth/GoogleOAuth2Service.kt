package com.ongo.infrastructure.security.oauth

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

        return OAuth2ErrorReporter.report("Google", "토큰 발급") {
            restClient.post()
                .uri("https://oauth2.googleapis.com/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(params)
                .retrieve()
                .body(GoogleTokenResponse::class.java)
        } ?: throw UnauthorizedException("Google 토큰 발급에 실패했습니다")
    }

    private fun fetchUserInfo(tokenResponse: GoogleTokenResponse): OAuth2UserInfo {
        val userInfo = OAuth2ErrorReporter.report("Google", "사용자 조회") {
            restClient.get()
                .uri("https://www.googleapis.com/oauth2/v2/userinfo")
                .header("Authorization", "Bearer ${tokenResponse.accessToken}")
                .retrieve()
                .body(GoogleUserInfo::class.java)
        } ?: throw UnauthorizedException("Google 사용자 정보를 가져올 수 없습니다")

        // 식별자가 비면 로그인을 실패시킨다. 계정을 만들어서는 안 된다.
        // providerId 는 findByProviderAndProviderId 의 조회 키다. 빈 값으로 저장되면
        // 이후 모든 구글 사용자가 같은 행에 매칭돼 서로의 계정으로 로그인될 수 있다.
        if (userInfo.id.isBlank()) {
            throw UnauthorizedException("Google 사용자 식별자를 받지 못했습니다")
        }
        if (userInfo.email.isBlank()) {
            throw UnauthorizedException("Google 계정 이메일을 받지 못했습니다")
        }

        return OAuth2UserInfo(
            providerId = userInfo.id,
            email = userInfo.email,
            name = userInfo.name,
            profileImageUrl = userInfo.picture,
        )
    }

    /** Jackson 3 binds these mutable properties by their wire names. */
    class GoogleTokenResponse {
        var access_token: String = ""
        var token_type: String = ""
        var expires_in: Int = 0
        var scope: String = ""
        var id_token: String? = null

        val accessToken: String get() = access_token
        val tokenType: String get() = token_type
        val expiresIn: Int get() = expires_in
        val idToken: String? get() = id_token
    }

    /** Jackson 3 can populate these mutable properties through their setters. */
    class GoogleUserInfo {
        var id: String = ""
        var email: String = ""
        var name: String = ""
        var picture: String? = null
    }
}
