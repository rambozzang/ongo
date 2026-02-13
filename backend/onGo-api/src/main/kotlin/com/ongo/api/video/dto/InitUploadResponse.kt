package com.ongo.api.video.dto

data class InitUploadResponse(
    val videoId: Long,
    val uploadUrl: String,
    val tusEndpoint: String,
)
