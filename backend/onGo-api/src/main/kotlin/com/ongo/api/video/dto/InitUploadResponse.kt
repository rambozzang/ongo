package com.ongo.api.video.dto

import com.ongo.common.enums.MediaType

data class InitUploadResponse(
    val videoId: Long,
    val uploadUrl: String,
    val tusEndpoint: String,
    val mediaType: MediaType,
)
