package com.ongo.application.video

import com.ongo.common.enums.Platform

interface PlatformUploadService {
    fun supports(platform: Platform): Boolean
    fun upload(config: PlatformUploadConfig, fileUrl: String, userId: Long): PlatformUploadResult
}

data class PlatformUploadResult(
    val success: Boolean,
    val platformVideoId: String? = null,
    val platformUrl: String? = null,
    val errorMessage: String? = null,
)
