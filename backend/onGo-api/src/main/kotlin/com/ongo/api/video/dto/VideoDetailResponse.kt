package com.ongo.api.video.dto

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.common.enums.VariantStatus
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
    val status: UploadStatus,
    val uploads: List<PlatformUploadDetail>,
    val variants: List<VideoVariantItem> = emptyList(),
    val createdAt: LocalDateTime?,
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

data class VideoVariantItem(
    val platform: Platform,
    val status: VariantStatus,
    val fileUrl: String?,
    val fileSizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val errorMessage: String?,
)
