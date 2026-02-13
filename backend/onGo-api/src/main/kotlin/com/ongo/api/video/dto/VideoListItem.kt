package com.ongo.api.video.dto

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import java.time.LocalDateTime

data class VideoListItem(
    val id: Long,
    val title: String,
    val thumbnailUrl: String?,
    val status: UploadStatus,
    val uploads: List<PlatformStatusItem>,
    val totalViews: Long,
    val createdAt: LocalDateTime?,
)

data class PlatformStatusItem(
    val platform: Platform,
    val status: UploadStatus,
    val platformUrl: String? = null,
)
