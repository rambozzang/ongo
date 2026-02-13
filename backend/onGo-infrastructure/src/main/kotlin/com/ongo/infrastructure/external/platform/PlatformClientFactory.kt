package com.ongo.infrastructure.external.platform

import com.ongo.common.enums.Platform
import org.springframework.stereotype.Component

@Component
class PlatformClientFactory(private val clients: List<PlatformClient>) {

    private val clientMap: Map<Platform, PlatformClient> by lazy {
        clients.associateBy { it.platform }
    }

    fun getClient(platform: Platform): PlatformClient =
        clientMap[platform]
            ?: throw IllegalArgumentException("지원하지 않는 플랫폼입니다: $platform")
}
