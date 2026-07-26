package com.ongo.api.video.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class PresignedUploadRequest(
    @field:NotBlank val filename: String,
    @field:Positive val fileSize: Long,
    @field:NotBlank val contentType: String,
)

data class PresignedUploadResponse(
    val videoId: Long,
    val uploadUrl: String,
)
