package com.ongo.domain.video.entity

import java.time.LocalDateTime

data class VideoResize(
    val id: Long? = null,
    val userId: Long,
    val originalVideoId: Long,
    val aspectRatio: String,
    val width: Int,
    val height: Int,
    val fileUrl: String? = null,
    val fileSizeBytes: Long? = null,
    val status: String = "PENDING",
    val errorMessage: String? = null,
    val createdAt: LocalDateTime? = null,
    val completedAt: LocalDateTime? = null,
)
