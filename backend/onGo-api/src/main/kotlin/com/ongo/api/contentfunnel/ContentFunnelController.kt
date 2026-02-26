package com.ongo.api.contentfunnel

import com.ongo.application.contentfunnel.ContentFunnelUseCase
import com.ongo.application.contentfunnel.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "콘텐츠 퍼널 분석", description = "시청~전환 퍼널 분석")
@RestController
@RequestMapping("/api/v1/content-funnel")
class ContentFunnelController(
    private val contentFunnelUseCase: ContentFunnelUseCase
) {

    @Operation(summary = "퍼널 단계 조회")
    @GetMapping("/{videoId}/stages")
    fun getStages(
        @PathVariable videoId: Long,
    ): ResponseEntity<ResData<List<FunnelStageResponse>>> {
        val result = contentFunnelUseCase.getStages(videoId)
        return ResData.success(result)
    }

    @Operation(summary = "퍼널 비교")
    @GetMapping("/compare")
    fun compare(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam videoIdA: Long,
        @RequestParam videoIdB: Long,
    ): ResponseEntity<ResData<FunnelComparisonResponse>> {
        val result = contentFunnelUseCase.compare(userId, videoIdA, videoIdB)
        return ResData.success(result)
    }

    @Operation(summary = "퍼널 분석 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<ContentFunnelSummaryResponse>> {
        val result = contentFunnelUseCase.getSummary(userId)
        return ResData.success(result)
    }
}
