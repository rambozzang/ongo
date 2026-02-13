package com.ongo.infrastructure.security.oauth

import com.ongo.common.enums.AuthProvider
import com.ongo.common.exception.UnauthorizedException
import com.ongo.domain.auth.OAuth2Port
import com.ongo.domain.auth.OAuth2UserResult
import org.springframework.stereotype.Component

@Component
class OAuth2ServiceFactory(
    private val googleOAuth2Service: GoogleOAuth2Service,
    private val kakaoOAuth2Service: KakaoOAuth2Service,
) : OAuth2Port {

    override fun getUserInfo(provider: AuthProvider, code: String, redirectUri: String): OAuth2UserResult {
        val userInfo = when (provider) {
            AuthProvider.GOOGLE -> googleOAuth2Service.getUserInfo(code, redirectUri)
            AuthProvider.KAKAO -> kakaoOAuth2Service.getUserInfo(code, redirectUri)
        }
        return OAuth2UserResult(
            providerId = userInfo.providerId,
            email = userInfo.email,
            name = userInfo.name,
            profileImageUrl = userInfo.profileImageUrl,
        )
    }

    override fun resolveProvider(provider: String): AuthProvider {
        return when (provider.lowercase()) {
            "google" -> AuthProvider.GOOGLE
            "kakao" -> AuthProvider.KAKAO
            else -> throw UnauthorizedException("지원하지 않는 로그인 방식입니다: $provider")
        }
    }
}
