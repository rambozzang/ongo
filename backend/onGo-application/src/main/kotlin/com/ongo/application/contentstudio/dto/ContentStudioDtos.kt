package com.ongo.application.contentstudio.dto

import java.time.LocalDateTime

data class ContentClipResponse(
    val id: Long,
    val sourceVideoId: Long?,
    val title: String?,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val aspectRatio: String,
    val status: String,
    val outputUrl: String?,
    val createdAt: LocalDateTime?,
)

data class CreateClipRequest(
    val sourceVideoId: Long? = null,
    val title: String? = null,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val aspectRatio: String = "9:16",
)

data class VideoCaptionResponse(
    val id: Long,
    val videoId: Long,
    val language: String,
    val captionData: String,
    val status: String,
    val createdAt: LocalDateTime?,
)

data class CreateCaptionRequest(
    val videoId: Long,
    val language: String = "ko",
    val captionData: String,
)

data class AiThumbnailResponse(
    val id: Long,
    val videoId: Long,
    val style: String?,
    val textOverlay: String?,
    val imageUrl: String?,
    val createdAt: LocalDateTime?,
)

data class GenerateThumbnailRequest(
    val videoId: Long,
    val style: String? = null,
    val textOverlay: String? = null,
)

data class VideoSummaryResponse(
    val id: Long,
    val title: String,
    val duration: Int?,
    val thumbnailUrl: String?,
    val fileUrl: String?,
)

data class ContentStudioHistoryResponse(
    val id: Long,
    val type: String,
    val videoTitle: String,
    val description: String,
    val creditsUsed: Int,
    val createdAt: LocalDateTime?,
)
