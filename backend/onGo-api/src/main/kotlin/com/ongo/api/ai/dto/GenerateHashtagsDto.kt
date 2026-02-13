package com.ongo.api.ai.dto

import com.ongo.common.enums.Platform
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class GenerateHashtagsRequest(
    @field:NotBlank(message = "제목은 필수입니다")
    @field:Size(max = 200, message = "제목은 최대 200자까지 입력할 수 있습니다")
    val title: String,
    @field:NotBlank(message = "카테고리는 필수입니다")
    @field:Size(max = 100, message = "카테고리는 최대 100자까지 입력할 수 있습니다")
    val category: String,
    @field:NotEmpty(message = "타겟 플랫폼을 1개 이상 선택해주세요")
    @field:Size(max = 4, message = "타겟 플랫폼은 최대 4개까지 선택할 수 있습니다")
    val targetPlatforms: List<Platform>,
)

data class GenerateHashtagsResponse(
    val platforms: List<PlatformHashtags>,
) {
    data class PlatformHashtags(
        val platform: String,
        val hashtags: List<String>,
    )
}
