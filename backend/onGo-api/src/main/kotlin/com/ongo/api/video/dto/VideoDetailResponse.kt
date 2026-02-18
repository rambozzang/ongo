package com.ongo.api.video.dto

import com.ongo.common.enums.MediaType
import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.common.enums.Visibility
import java.time.LocalDateTime

data class VideoDetailResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val tags: List<String>,
    val category: String?,
    val fileUrl: String?,
    val fileSize: Long?,
    val duration: Int?,
    val resolution: String?,
    val thumbnails: List<String>,
    val autoThumbnails: List<String> = emptyList(),
    val selectedThumbnailIndex: Int = 0,
    val mediaType: MediaType = MediaType.VIDEO,
    val status: UploadStatus,
    val contentImages: List<ContentImageItem> = emptyList(),
    val uploads: List<PlatformUploadDetail>,
    val createdAt: LocalDateTime?,
)

data class ContentImageItem(
    val id: Long?,
    val imageUrl: String,
    val displayOrder: Int,
    val width: Int?,
    val height: Int?,
    val fileSizeBytes: Long?,
    val originalFilename: String?,
)

data class PlatformUploadDetail(
    val id: Long,
    val platform: Platform,
    val status: UploadStatus,
    val platformVideoId: String?,
    val platformUrl: String?,
    val errorMessage: String?,
    val publishedAt: LocalDateTime?,
    val meta: PlatformMetaDetail?,
)

data class PlatformMetaDetail(
    val title: String?,
    val description: String?,
    val tags: List<String>,
    val visibility: Visibility,
    val customThumbnailUrl: String?,
)
