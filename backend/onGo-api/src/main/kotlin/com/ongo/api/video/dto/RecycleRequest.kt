package com.ongo.api.video.dto

import com.ongo.common.enums.Platform
import com.ongo.common.enums.Visibility
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class RecycleRequest(
    @field:NotBlank(message = "제목은 필수입니다")
    @field:Size(max = 100, message = "제목은 최대 100자까지 입력할 수 있습니다")
    val title: String,
    @field:Size(max = 5000, message = "설명은 최대 5,000자까지 입력할 수 있습니다")
    val description: String? = null,
    @field:Size(max = 30, message = "태그는 최대 30개까지 입력할 수 있습니다")
    val tags: List<String> = emptyList(),
    val category: String? = null,
    @field:NotEmpty(message = "재게시할 플랫폼을 하나 이상 선택해주세요")
    @field:Size(max = 13, message = "플랫폼은 최대 13개까지 선택할 수 있습니다")
    @field:Valid
    val platforms: List<RecyclePlatformRequest>,
)

data class RecyclePlatformRequest(
    val platform: Platform,
    val channelId: Long? = null,
    @field:Size(max = 100, message = "플랫폼 제목은 최대 100자까지 입력할 수 있습니다")
    val title: String? = null,
    @field:Size(max = 5000, message = "플랫폼 설명은 최대 5,000자까지 입력할 수 있습니다")
    val description: String? = null,
    @field:Size(max = 30, message = "플랫폼 태그는 최대 30개까지 입력할 수 있습니다")
    val tags: List<String> = emptyList(),
    val visibility: Visibility = Visibility.PUBLIC,
    val thumbnailUrl: String? = null,
    val scheduledAt: LocalDateTime? = null,
)
