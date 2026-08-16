package com.ongo.application.channel

import com.ongo.application.channel.dto.ChannelOAuthAuthorizationResponse
import com.ongo.application.platform.PlatformConfigurationPort
import com.ongo.common.enums.Platform
import com.ongo.common.exception.BusinessException
import com.ongo.domain.channel.PlatformOAuthAuthorizationPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI

/**
 * Creates the normal authenticated channel-connect URL.
 *
 * The browser supplies only the callback origin, state and (for X) PKCE
 * challenge. Client IDs, scopes and provider endpoints remain server-owned so
 * a successful capability response cannot be followed by a stale frontend
 * environment failure.
 */
@Service
class ChannelOAuthAuthorizationUseCase(
    private val authorizationPort: PlatformOAuthAuthorizationPort,
    private val platformConfigurationPort: PlatformConfigurationPort,
    @param:Value("\${cors.allowed-origins:}") private val allowedOrigins: String,
) {
    fun authorizationUrl(
        platformValue: String,
        redirectUri: String,
        state: String,
        codeChallenge: String?,
    ): ChannelOAuthAuthorizationResponse {
        val platform = parsePlatform(platformValue)
        val configuration = platformConfigurationPort.status(platform)
        if (!configuration.configured) {
            throw BusinessException(
                "PLATFORM_NOT_CONFIGURED",
                configuration.reason ?: "${platform.name} 플랫폼 연동 설정이 없어 연결할 수 없습니다.",
            )
        }
        validateRedirectUri(redirectUri)
        require(state.isNotBlank() && state.length <= MAX_STATE_LENGTH) {
            "OAuth state가 올바르지 않습니다"
        }
        if (platform == Platform.TWITTER) {
            require(!codeChallenge.isNullOrBlank()) {
                "Twitter OAuth에는 PKCE code_challenge가 필요합니다"
            }
        }
        return ChannelOAuthAuthorizationResponse(
            authorizationPort.buildAuthorizationUrl(platform, redirectUri, state, codeChallenge),
        )
    }

    private fun parsePlatform(value: String): Platform = runCatching {
        Platform.valueOf(value.trim().uppercase().replace('-', '_'))
    }.getOrElse {
        throw BusinessException("UNSUPPORTED_PLATFORM", "지원하지 않는 플랫폼입니다: $value")
    }

    private fun validateRedirectUri(value: String) {
        val uri = runCatching { URI(value) }.getOrElse {
            throw IllegalArgumentException("OAuth redirect URI가 올바르지 않습니다")
        }
        require(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) {
            "OAuth redirect URI는 http(s)만 허용됩니다"
        }
        require(uri.userInfo == null && uri.path == CALLBACK_PATH && uri.query == null && uri.fragment == null) {
            "OAuth redirect URI는 /auth/channel-callback 이어야 합니다"
        }
        val origin = "${uri.scheme.lowercase()}://${uri.authority}".trimEnd('/')
        val allowed = allowedOrigins.split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .map { it.trimEnd('/') }
            .toSet()
        require(origin in allowed) {
            "허용되지 않은 OAuth redirect origin입니다"
        }
    }

    companion object {
        private const val CALLBACK_PATH = "/auth/channel-callback"
        private const val MAX_STATE_LENGTH = 1024
    }
}
