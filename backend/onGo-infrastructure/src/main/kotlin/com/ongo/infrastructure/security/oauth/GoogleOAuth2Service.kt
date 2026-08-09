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
        //
        // providerId 는 findByProviderAndProviderId 의 조회 키다. 빈 값으로 저장되면
        // 이후 모든 구글 사용자가 같은 행에 매칭돼 **서로의 계정으로 로그인된다.**
        // 실제로 운영에서 매핑이 조용히 실패해 provider_id/email 이 빈 사용자가 만들어졌다.
        // 여기서 fail-closed 로 막으면 같은 종류의 실패가 또 나도 피해가 로그인 실패에 그친다.
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

    data class GoogleTokenResponse(
        @JsonProperty("access_token") val accessToken: String = "",
        @JsonProperty("token_type") val tokenType: String = "",
        @JsonProperty("expires_in") val expiresIn: Int = 0,
        @JsonProperty("scope") val scope: String = "",
        @JsonProperty("id_token") val idToken: String? = null,
    )

    /**
     * 모든 필드에 @JsonProperty 를 명시한다. 생략하면 안 된다.
     *
     * Kotlin data class 는 모든 파라미터에 기본값이 있으면 no-arg 생성자를 합성한다.
     * RestClient.create() 가 만드는 기본 메시지 컨버터에서는 Jackson 이 그 생성자로
     * 빈 객체를 만든 뒤 val 필드에 값을 주입하지 못해 **모든 필드가 조용히 기본값이 된다.**
     * 예외가 나지 않아 가입이 성공해버리는 것이 이 실패의 위험한 점이다.
     *
     * 같은 파일의 GoogleTokenResponse 가 멀쩡했던 이유도 여기 있다.
     * snake_case 라서 @JsonProperty 를 붙일 수밖에 없었을 뿐이다.
     */
    data class GoogleUserInfo(
        @JsonProperty("id") val id: String = "",
        @JsonProperty("email") val email: String = "",
        @JsonProperty("name") val name: String = "",
        @JsonProperty("picture") val picture: String? = null,
    )
}
