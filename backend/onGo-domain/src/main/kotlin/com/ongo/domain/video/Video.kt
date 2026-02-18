package com.ongo.domain.video

import com.ongo.common.enums.MediaType
import com.ongo.common.enums.UploadStatus
import java.time.LocalDateTime

data class Video(
    val id: Long? = null,
    val userId: Long,
    val title: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val category: String? = null,
    val fileUrl: String? = null,
    val fileSizeBytes: Long? = null,
    val durationSeconds: Int? = null,
    val resolution: String? = null,
    val originalFilename: String? = null,
    val contentHash: String? = null,
    val thumbnailUrls: List<String> = emptyList(),
    val autoThumbnails: List<String> = emptyList(),
    val selectedThumbnailIndex: Int = 0,
    val mediaType: MediaType = MediaType.VIDEO,
    val status: UploadStatus = UploadStatus.DRAFT,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
