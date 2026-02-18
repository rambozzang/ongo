package com.ongo.api.video.dto

import com.ongo.common.enums.MediaType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class InitUploadRequest(
    @field:NotBlank(message = "파일명은 필수입니다")
    @field:Size(max = 255, message = "파일명은 최대 255자까지 입력할 수 있습니다")
    val filename: String,

    @field:Positive(message = "파일 크기는 0보다 커야 합니다")
    val fileSize: Long,

    @field:NotBlank(message = "Content-Type은 필수입니다")
    @field:Size(max = 100, message = "Content-Type은 최대 100자까지 입력할 수 있습니다")
    val contentType: String,

    val mediaType: MediaType? = null,
)
