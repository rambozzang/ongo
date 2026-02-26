package com.ongo.api.videoseo

import com.ongo.application.videoseo.VideoSeoUseCase
import com.ongo.application.videoseo.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "비디오 SEO 최적화", description = "비디오 검색 최적화 분석 및 키워드 추천")
@RestController
@RequestMapping("/api/v1/video-seo")
class VideoSeoController(
    private val useCase: VideoSeoUseCase
) {

    @Operation(summary = "SEO 분석 목록 조회")
    @GetMapping
    fun getAnalyses(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) platform: String?,
    ): ResponseEntity<ResData<List<SeoAnalysisResponse>>> {
        val result = useCase.getAnalyses(userId, platform)
        return ResData.success(result)
    }

    @Operation(summary = "SEO 분석 실행")
    @PostMapping("/analyze")
    fun analyze(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: SeoOptimizeRequest,
    ): ResponseEntity<ResData<SeoAnalysisResponse>> {
        val result = useCase.analyze(userId, request)
        return ResData.success(result)
    }

    @Operation(summary = "SEO 키워드 목록 조회")
    @GetMapping("/{analysisId}/keywords")
    fun getKeywords(
        @PathVariable analysisId: Long,
    ): ResponseEntity<ResData<List<SeoKeywordResponse>>> {
        val result = useCase.getKeywords(analysisId)
        return ResData.success(result)
    }

    @Operation(summary = "SEO 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<VideoSeoSummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
