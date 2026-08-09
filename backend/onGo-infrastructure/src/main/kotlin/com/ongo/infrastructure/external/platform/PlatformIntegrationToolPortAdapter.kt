package com.ongo.infrastructure.external.platform

import com.ongo.application.publicapi.PlatformIntegrationToolPort
import com.ongo.application.publicapi.PlatformToolDefinition
import com.ongo.common.enums.Platform
import com.ongo.domain.channel.PlainToken
import org.springframework.stereotype.Component

/** Bridges public API tool discovery to the concrete provider client. */
@Component
class PlatformIntegrationToolPortAdapter(
    private val platformClientFactory: PlatformClientFactory,
) : PlatformIntegrationToolPort {
    override fun definitions(platform: Platform): List<PlatformToolDefinition> =
        platformClientFactory.getClient(platform).integrationTools()

    override fun invoke(
        platform: Platform,
        accessToken: PlainToken,
        platformChannelId: String?,
        methodName: String,
        data: Map<String, Any?>,
    ): Any? = platformClientFactory.getClient(platform).invokeIntegrationTool(
        accessToken = accessToken.value,
        platformChannelId = platformChannelId,
        methodName = methodName,
        data = data,
    )
}
