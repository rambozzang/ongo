package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.Visibility
import java.time.LocalDateTime

data class VideoPublishEvent(
    val videoId: Long,
    val userId: Long,
    val fileUrl: String,
    val platformConfigs: List<PlatformUploadConfig>,
)

data class PlatformUploadConfig(
    val platform: Platform,
    val videoUploadId: Long,
    val title: String,
    val description: String?,
    val tags: List<String>,
    val visibility: Visibility,
    val thumbnailUrl: String?,
    val fileSize: Long = 0,
    val scheduledAt: LocalDateTime?,
)

data class UploadCompletedEvent(
    val videoId: Long,
    val userId: Long,
    val platform: Platform,
    val success: Boolean,
    val platformUrl: String? = null,
    val errorMessage: String? = null,
)
