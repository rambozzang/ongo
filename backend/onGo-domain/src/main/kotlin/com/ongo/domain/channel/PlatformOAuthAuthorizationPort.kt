package com.ongo.domain.channel

import com.ongo.common.enums.Platform

/**
 * Builds a provider authorization URL from server-owned credentials.
 * Browser bundles must not carry a second, potentially stale copy of the
 * provider client IDs or scopes.
 */
interface PlatformOAuthAuthorizationPort {
    fun buildAuthorizationUrl(
        platform: Platform,
        redirectUri: String,
        state: String,
        codeChallenge: String? = null,
    ): String
}
