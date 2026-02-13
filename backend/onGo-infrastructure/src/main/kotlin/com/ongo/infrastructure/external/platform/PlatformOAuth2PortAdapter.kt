package com.ongo.infrastructure.external.platform

import com.ongo.common.enums.Platform
import com.ongo.domain.channel.PlatformOAuth2Port
import com.ongo.domain.channel.PlatformOAuth2TokenResult
import org.springframework.stereotype.Component

@Component
class PlatformOAuth2PortAdapter(
    private val platformClientFactory: PlatformClientFactory,
) : PlatformOAuth2Port {

    override fun exchangeCodeForTokens(
        platform: Platform,
        authorizationCode: String,
        redirectUri: String,
    ): PlatformOAuth2TokenResult {
        val client = platformClientFactory.getClient(platform)
        val result = client.exchangeCodeForTokens(authorizationCode, redirectUri)
        return PlatformOAuth2TokenResult(
            accessToken = result.accessToken,
            refreshToken = result.refreshToken,
            expiresIn = result.expiresIn,
        )
    }
}
