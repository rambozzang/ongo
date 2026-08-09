package com.ongo.api.video.dto

import jakarta.validation.constraints.Size
import jakarta.validation.Valid

data class UpdateVideoRequest(
    @field:Size(min = 1, max = 100, message = "제목은 1~100자여야 합니다")
    val title: String? = null,

    @field:Size(max = 5000, message = "설명은 최대 5,000자까지 입력 가능합니다")
    val description: String? = null,

    @field:Size(max = 30, message = "태그는 최대 30개까지 입력 가능합니다")
    val tags: List<String>? = null,

    val category: String? = null,

    val thumbnailIndex: Int? = null,

    /** 저장 시 선택한 플랫폼별 제목/설명/태그를 함께 보존합니다. */
    @field:Size(max = 13, message = "플랫폼은 최대 13개까지 선택할 수 있습니다")
    @field:Valid
    val platforms: List<PlatformDraftRequest>? = null,
)
