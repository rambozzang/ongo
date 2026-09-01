package com.ongo.api.video.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "에셋 승격 결과")
data class FromAssetResponse(
    @field:Schema(description = "새로 만들어진 영상 초안 ID. 작성 화면이 이 값으로 진입한다")
    val videoId: Long,
)
