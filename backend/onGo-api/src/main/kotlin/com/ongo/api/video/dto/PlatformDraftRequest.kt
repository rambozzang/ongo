package com.ongo.api.video.dto

import com.ongo.common.enums.Platform
import com.ongo.common.enums.Visibility
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class PlatformDraftRequest(
    val platform: Platform,
    val channelId: Long? = null,

    @field:NotBlank(message = "플랫폼별 제목을 입력해주세요")
    @field:Size(max = 2200, message = "플랫폼별 제목은 최대 2,200자까지 입력 가능합니다")
    val title: String,

    @field:Size(max = 5000, message = "플랫폼별 설명은 최대 5,000자까지 입력 가능합니다")
    val description: String? = null,

    @field:Size(max = 500, message = "태그는 최대 500개까지 입력 가능합니다")
    val tags: List<String> = emptyList(),

    val visibility: Visibility = Visibility.PUBLIC,
    val customThumbnailUrl: String? = null,
)
