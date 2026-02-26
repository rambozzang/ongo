package com.ongo.api.audienceoverlap

import com.ongo.application.audienceoverlap.AudienceOverlapUseCase
import com.ongo.application.audienceoverlap.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "오디언스 오버랩", description = "크로스 플랫폼 오디언스 중복 분석")
@RestController
@RequestMapping("/api/v1/audience-overlap")
class AudienceOverlapController(
    private val useCase: AudienceOverlapUseCase
) {

    @Operation(summary = "오버랩 분석 결과 목록 조회")
    @GetMapping
    fun getResults(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<AudienceOverlapResultResponse>>> {
        val result = useCase.getResults(userId)
        return ResData.success(result)
    }

    @Operation(summary = "오버랩 세그먼트 목록 조회")
    @GetMapping("/segments")
    fun getSegments(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<OverlapSegmentResponse>>> {
        val result = useCase.getSegments(userId)
        return ResData.success(result)
    }

    @Operation(summary = "오디언스 오버랩 분석 실행")
    @PostMapping("/analyze")
    fun analyze(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: AnalyzeOverlapRequest,
    ): ResponseEntity<ResData<AudienceOverlapResultResponse>> {
        val result = useCase.analyze(userId, request)
        return ResData.success(result)
    }

    @Operation(summary = "오버랩 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<AudienceOverlapSummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
