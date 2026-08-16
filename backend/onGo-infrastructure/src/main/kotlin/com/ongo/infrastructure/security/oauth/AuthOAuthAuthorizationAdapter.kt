package com.ongo.infrastructure.security.oauth

import com.ongo.common.exception.BusinessException
import com.ongo.domain.auth.AuthOAuthAuthorizationPort
import com.ongo.domain.auth.AuthOAuthProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

/** Server-owned client IDs for the unauthenticated Google/Kakao login flow. */
@Component
class AuthOAuthAuthorizationAdapter(
    @param:Value("\${spring.security.oauth2.client.registration.google.client-id:}") private val googleClientId: String,
    @param:Value("\${spring.security.oauth2.client.registration.kakao.client-id:}") private val kakaoClientId: String,
) : AuthOAuthAuthorizationPort {
    override fun buildAuthorizationUrl(
        provider: AuthOAuthProvider,
        redirectUri: String,
        state: String,
    ): String {
        val clientId = when (provider) {
            AuthOAuthProvider.GOOGLE -> googleClientId
            AuthOAuthProvider.KAKAO -> kakaoClientId
        }.trim()
        if (clientId.isBlank() || listOf("dummy", "placeholder", "change-me", "your-").any(clientId.lowercase()::contains)) {
            throw BusinessException("OAUTH_NOT_CONFIGURED", "${provider.name} OAuth client가 설정되지 않았습니다")
        }

        val builder = UriComponentsBuilder.fromUriString(
            when (provider) {
                AuthOAuthProvider.GOOGLE -> "https://accounts.google.com/o/oauth2/v2/auth"
                AuthOAuthProvider.KAKAO -> "https://kauth.kakao.com/oauth/authorize"
            },
        )
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("response_type", "code")
            .queryParam("state", state)

        if (provider == AuthOAuthProvider.GOOGLE) {
            builder.queryParam("scope", "openid email profile")
        } else {
            // Keep this aligned with spring.security.oauth2.client.registration.kakao.scope.
            // Email remains mandatory in KakaoOAuth2Service, so the consent request must
            // explicitly ask for it once the Kakao business app has that permission.
            builder.queryParam("scope", "profile_nickname,account_email")
        }
        return builder.build().encode().toUriString()
    }
}
