package com.ongo.api.video.dto

import com.ongo.common.enums.Platform
import com.ongo.common.enums.Visibility
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import java.time.LocalDateTime

data class StreamPublishMetadataRequest(
    @field:NotBlank
    val title: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val category: String? = null,
    val thumbnailUrl: String? = null,
    @field:NotEmpty
    @field:Valid
    val platforms: List<PlatformPublishConfigRequest>,
)

data class PlatformPublishConfigRequest(
    val platform: Platform,
    val channelId: Long? = null,
    val title: String? = null,
    val description: String? = null,
    val tags: List<String>? = null,
    val visibility: Visibility = Visibility.PUBLIC,
    val scheduledAt: LocalDateTime? = null,
)

data class StreamPublishResponse(
    val videoId: Long,
    val message: String = "업로드가 시작되었습니다. 각 플랫폼 업로드 상태는 영상 상세 페이지에서 확인하세요.",
)
