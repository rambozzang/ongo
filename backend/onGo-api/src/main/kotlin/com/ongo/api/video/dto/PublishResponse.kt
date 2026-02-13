package com.ongo.api.video.dto

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus

data class PublishResponse(
    val videoId: Long,
    val uploads: List<UploadStatusItem>,
)

data class UploadStatusItem(
    val platform: Platform,
    val status: UploadStatus,
    val errorMessage: String? = null,
)
