package com.ongo.api.video.dto

import jakarta.validation.constraints.Size

data class UpdateVideoRequest(
    @field:Size(min = 1, max = 100, message = "제목은 1~100자여야 합니다")
    val title: String? = null,

    @field:Size(max = 5000, message = "설명은 최대 5,000자까지 입력 가능합니다")
    val description: String? = null,

    @field:Size(max = 30, message = "태그는 최대 30개까지 입력 가능합니다")
    val tags: List<String>? = null,

    val category: String? = null,

    val thumbnailIndex: Int? = null,
)
