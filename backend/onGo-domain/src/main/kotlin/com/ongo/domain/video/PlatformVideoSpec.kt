package com.ongo.domain.video

import com.ongo.common.enums.Platform

data class PlatformVideoSpec(
    val width: Int,
    val height: Int,
    val bitrateKbps: Int,
    val codec: String = "libx264",
    val audioCodec: String = "aac",
    val audioBitrate: String = "128k",
) {
    companion object {
        fun forPlatform(platform: Platform): PlatformVideoSpec = when (platform) {
            Platform.YOUTUBE -> PlatformVideoSpec(1920, 1080, 8000)
            Platform.TIKTOK -> PlatformVideoSpec(1080, 1920, 4000)
            Platform.INSTAGRAM -> PlatformVideoSpec(1080, 1920, 4000)
            Platform.NAVER_CLIP -> PlatformVideoSpec(1080, 1920, 4000)
        }
    }
}
