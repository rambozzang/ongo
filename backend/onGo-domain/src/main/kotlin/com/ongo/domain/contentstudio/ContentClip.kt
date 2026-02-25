package com.ongo.domain.contentstudio

import java.time.LocalDateTime

data class ContentClip(
    val id: Long? = null,
    val userId: Long,
    val sourceVideoId: Long? = null,
    val title: String? = null,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val aspectRatio: String = "9:16",
    val status: String = "PENDING",
    val outputUrl: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)

data class VideoCaption(
    val id: Long? = null,
    val videoId: Long,
    val language: String = "ko",
    val captionData: String, // JSONB as String
    val status: String = "DRAFT",
    val createdAt: LocalDateTime? = null,
)

data class AiThumbnail(
    val id: Long? = null,
    val videoId: Long,
    val style: String? = null,
    val textOverlay: String? = null,
    val imageUrl: String? = null,
    val createdAt: LocalDateTime? = null,
)
