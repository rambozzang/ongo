package com.ongo.application.video.dto

data class ResizeRequest(
    val aspectRatios: List<String>,
)

data class VideoResizeResponse(
    val id: Long,
    val originalVideoId: Long,
    val aspectRatio: String,
    val width: Int,
    val height: Int,
    val fileUrl: String?,
    val status: String,
    val createdAt: String?,
)
