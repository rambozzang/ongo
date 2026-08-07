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

/**
 * 프론트가 진입점 노출 여부를 정하는 데 쓴다.
 *
 * [reason] 은 사용자에게 그대로 보여줄 수 있어야 한다. 경로나 예외 메시지를 담지 않는다.
 */
data class VideoDownloadAvailabilityResponse(
    val available: Boolean,
    val reason: String? = null,
)
