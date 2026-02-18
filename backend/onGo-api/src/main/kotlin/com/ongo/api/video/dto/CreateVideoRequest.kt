package com.ongo.api.video.dto

import com.ongo.common.enums.MediaType
import com.ongo.common.enums.Visibility
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateVideoRequest(
    @field:NotBlank(message = "제목은 필수입니다")
    @field:Size(min = 1, max = 100, message = "제목은 1~100자여야 합니다")
    val title: String,

    @field:Size(max = 5000, message = "설명은 최대 5,000자까지 입력 가능합니다")
    val description: String? = null,

    @field:Size(max = 30, message = "태그는 최대 30개까지 입력 가능합니다")
    val tags: List<String>? = null,

    val category: String? = null,
    val thumbnailUrl: String? = null,
    val visibility: Visibility = Visibility.PUBLIC,
    val mediaType: MediaType = MediaType.VIDEO,
)
