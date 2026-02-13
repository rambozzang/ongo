package com.ongo.domain.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import java.time.LocalDateTime

data class VideoUpload(
    val id: Long? = null,
    val videoId: Long,
    val platform: Platform,
    val platformVideoId: String? = null,
    val status: UploadStatus = UploadStatus.UPLOADING,
    val errorMessage: String? = null,
    val platformUrl: String? = null,
    val publishedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
