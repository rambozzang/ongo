package com.ongo.api.videoseo

import com.ongo.application.videoseo.VideoSeoUseCase
import com.ongo.application.videoseo.dto.VideoSeoScoreResponse
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "비디오 SEO", description = "영상 SEO 점수 분석")
@RestController
@RequestMapping("/api/v1/videos")
class VideoSeoController(
    private val videoSeoUseCase: VideoSeoUseCase,
) {

    @Operation(summary = "SEO 점수 분석", description = "AI가 영상 메타데이터(제목/설명/태그)를 분석하여 SEO 점수와 개선 제안을 제공합니다. 크레딧 2개 소모.")
    @PostMapping("/{videoId}/seo-score")
    fun analyzeSeoScore(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable videoId: Long,
    ): ResponseEntity<ResData<VideoSeoScoreResponse>> {
        val result = videoSeoUseCase.analyzeVideoSeo(userId, videoId)
        return ResData.success(result, "SEO 점수 분석이 완료되었습니다")
    }
}
