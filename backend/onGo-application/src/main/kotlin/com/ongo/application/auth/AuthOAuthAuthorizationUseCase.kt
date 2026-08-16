package com.ongo.application.auth

import com.ongo.common.exception.BusinessException
import com.ongo.domain.auth.AuthOAuthAuthorizationPort
import com.ongo.domain.auth.AuthOAuthProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI

/**
 * Creates the public social-login URL without exposing provider credentials to
 * the frontend build. The callback is still checked against the configured
 * origin so this endpoint cannot become an open redirect helper.
 */
@Service
class AuthOAuthAuthorizationUseCase(
    private val authorizationPort: AuthOAuthAuthorizationPort,
    @param:Value("\${cors.allowed-origins:}") private val allowedOrigins: String,
) {
    fun authorizationUrl(providerValue: String, redirectUri: String, state: String): String {
        val provider = parseProvider(providerValue)
        validateRedirectUri(provider, redirectUri)
        require(state.isNotBlank() && state.length <= MAX_STATE_LENGTH) {
            "OAuth state가 올바르지 않습니다"
        }
        return authorizationPort.buildAuthorizationUrl(provider, redirectUri, state)
    }

    private fun parseProvider(value: String): AuthOAuthProvider = runCatching {
        AuthOAuthProvider.valueOf(value.trim().uppercase())
    }.getOrElse {
        throw BusinessException("UNSUPPORTED_OAUTH_PROVIDER", "지원하지 않는 소셜 로그인입니다: $value")
    }

    private fun validateRedirectUri(provider: AuthOAuthProvider, value: String) {
        val uri = runCatching { URI(value) }.getOrElse {
            throw IllegalArgumentException("OAuth redirect URI가 올바르지 않습니다")
        }
        require(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) {
            "OAuth redirect URI는 http(s)만 허용됩니다"
        }
        require(
            uri.userInfo == null &&
                uri.path == "/auth/callback/${provider.name.lowercase()}" &&
                uri.query == null &&
                uri.fragment == null,
        ) {
            "OAuth redirect URI가 올바르지 않습니다"
        }
        val origin = "${uri.scheme.lowercase()}://${uri.authority}".trimEnd('/')
        val allowed = allowedOrigins.split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .map { it.trimEnd('/') }
            .toSet()
        require(origin in allowed) { "허용되지 않은 OAuth redirect origin입니다" }
    }

    companion object {
        private const val MAX_STATE_LENGTH = 1024
    }
}
