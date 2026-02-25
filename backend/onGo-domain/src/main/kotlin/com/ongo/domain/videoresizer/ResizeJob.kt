package com.ongo.domain.videoresizer

import java.math.BigDecimal
import java.time.LocalDateTime

data class ResizeJob(
    val id: Long? = null,
    val userId: Long,
    val videoId: Long,
    val videoTitle: String? = null,
    val originalAspectRatio: String? = null,
    val targetAspectRatio: String,
    val platform: String,
    val status: String = "PENDING",
    val progress: Int = 0,
    val outputUrl: String? = null,
    val thumbnailUrl: String? = null,
    val smartCrop: Boolean = true,
    val focusPointX: BigDecimal? = null,
    val focusPointY: BigDecimal? = null,
    val createdAt: LocalDateTime? = null,
    val completedAt: LocalDateTime? = null,
)
