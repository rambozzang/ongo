package com.ongo.api.video.dto

import com.ongo.common.enums.Platform
import com.ongo.common.enums.Visibility
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class PublishRequest(
    @field:NotEmpty(message = "게시할 플랫폼을 1개 이상 선택해주세요")
    @field:Size(max = 4, message = "플랫폼은 최대 4개까지 선택할 수 있습니다")
    @field:Valid
    val platforms: List<PlatformPublishConfig>,
)

data class PlatformPublishConfig(
    val platform: Platform,
    @field:Size(max = 100, message = "제목은 최대 100자까지 입력할 수 있습니다")
    val title: String? = null,
    @field:Size(max = 5000, message = "설명은 최대 5,000자까지 입력할 수 있습니다")
    val description: String? = null,
    @field:Size(max = 30, message = "태그는 최대 30개까지 입력할 수 있습니다")
    val tags: List<String>? = null,
    val visibility: Visibility = Visibility.PUBLIC,
    @field:Size(max = 2048, message = "썸네일 URL은 최대 2048자까지 입력할 수 있습니다")
    val thumbnailUrl: String? = null,
    val scheduledAt: LocalDateTime? = null,
)
