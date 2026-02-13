package com.ongo.api.ai.dto

import com.ongo.common.enums.Platform
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class GenerateMetaRequest(
    @field:Size(max = 10000, message = "스크립트는 최대 10000자까지 입력할 수 있습니다")
    val script: String? = null,
    val videoId: Long? = null,
    val useStt: Boolean = false,
    @field:NotEmpty(message = "타겟 플랫폼을 1개 이상 선택해주세요")
    @field:Size(max = 4, message = "타겟 플랫폼은 최대 4개까지 선택할 수 있습니다")
    val targetPlatforms: List<Platform>,
    @field:Size(max = 100, message = "톤은 최대 100자까지 입력할 수 있습니다")
    val tone: String = "friendly",
    @field:NotBlank(message = "카테고리는 필수입니다")
    @field:Size(max = 100, message = "카테고리는 최대 100자까지 입력할 수 있습니다")
    val category: String,
)

data class GenerateMetaResponse(
    val platforms: List<PlatformMeta>,
) {
    data class PlatformMeta(
        val platform: String,
        val titleCandidates: List<String>,
        val description: String,
        val hashtags: List<String>,
    )
}
