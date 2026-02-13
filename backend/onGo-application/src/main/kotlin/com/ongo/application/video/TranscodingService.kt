package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.domain.video.PlatformVideoSpec

interface TranscodingService {
    fun transcode(inputUrl: String, videoId: Long, platform: Platform, spec: PlatformVideoSpec): TranscodeResult
}

data class TranscodeResult(
    val fileUrl: String,
    val fileSizeBytes: Long,
    val width: Int,
    val height: Int,
)
