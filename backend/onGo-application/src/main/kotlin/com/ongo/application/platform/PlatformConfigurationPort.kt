package com.ongo.application.platform

import com.ongo.common.enums.Platform

/**
 * Reports whether the server has the provider credentials required to start an
 * OAuth connection or publish through a platform.
 *
 * This is deliberately separate from upload capability. A provider can be
 * supported by the codebase while being unavailable in a particular
 * deployment, and that state must be visible to both the UI and the write
 * paths.
 */
interface PlatformConfigurationPort {
    fun status(platform: Platform): PlatformConfigurationStatus
}

data class PlatformConfigurationStatus(
    val configured: Boolean,
    val reason: String? = null,
)
