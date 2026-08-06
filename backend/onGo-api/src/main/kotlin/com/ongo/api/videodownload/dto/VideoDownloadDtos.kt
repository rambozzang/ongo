package com.ongo.api.videodownload.dto

import com.ongo.domain.videodownload.VideoDownloadProvider
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class VideoDownloadRequest(
    @field:NotBlank(message = "영상 URL은 필수입니다")
    @field:Size(max = 2000, message = "영상 URL은 2,000자 이하여야 합니다")
    val url: String,
    @field:Size(max = 200, message = "제목은 200자 이하여야 합니다")
    val title: String? = null,
)

data class VideoDownloadResponse(
    val videoId: Long,
    val title: String,
    val provider: VideoDownloadProvider,
    val fileUrl: String?,
)
