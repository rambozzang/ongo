package com.ongo.application.publicapi

import com.ongo.domain.channel.PlainToken
import com.ongo.common.enums.Platform

/**
 * A provider operation exposed through the Postiz-compatible public API.
 *
 * The same definition is used by discovery and invocation. This prevents an
 * operation from being advertised in integration-settings and then silently
 * ignored by integration-trigger.
 */
data class PlatformToolDefinition(
    val methodName: String,
    val description: String,
    val dataSchema: List<PlatformToolField> = emptyList(),
)

data class PlatformToolField(
    val key: String,
    val type: String,
    val description: String,
)

interface PlatformIntegrationToolPort {
    fun definitions(platform: Platform): List<PlatformToolDefinition>

    fun invoke(
        platform: Platform,
        accessToken: PlainToken,
        platformChannelId: String?,
        methodName: String,
        data: Map<String, Any?>,
    ): Any?
}
