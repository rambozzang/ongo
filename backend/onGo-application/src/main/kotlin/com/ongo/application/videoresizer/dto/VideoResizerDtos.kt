package com.ongo.application.videoresizer.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class ResizeJobResponse(
    val id: Long,
    val videoId: Long,
    val videoTitle: String?,
    val originalAspectRatio: String?,
    val targetAspectRatio: String,
    val platform: String,
    val status: String,
    val progress: Int,
    val outputUrl: String?,
    val thumbnailUrl: String?,
    val smartCrop: Boolean,
    val focusPointX: BigDecimal?,
    val focusPointY: BigDecimal?,
    val createdAt: LocalDateTime?,
    val completedAt: LocalDateTime?,
)

data class CreateResizeJobRequest(
    val videoId: Long,
    val videoTitle: String? = null,
    val originalAspectRatio: String? = null,
    val targetAspectRatio: String,
    val platform: String,
    val smartCrop: Boolean = true,
    val focusPointX: BigDecimal? = null,
    val focusPointY: BigDecimal? = null,
)
