package com.ongo.api.subtitlegenerator

import com.ongo.application.subtitlegenerator.SubtitleGeneratorUseCase
import com.ongo.application.subtitlegenerator.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "AI 자막 생성기", description = "AI 기반 영상 자막 자동 생성")
@RestController
@RequestMapping("/api/v1/subtitle-generator")
class SubtitleGeneratorController(
    private val useCase: SubtitleGeneratorUseCase
) {

    @Operation(summary = "자막 작업 목록 조회")
    @GetMapping
    fun getJobs(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) status: String?,
    ): ResponseEntity<ResData<List<SubtitleJobResponse>>> {
        val result = useCase.getJobs(userId, status)
        return ResData.success(result)
    }

    @Operation(summary = "자막 작업 상세 조회")
    @GetMapping("/{id}")
    fun getJob(
        @PathVariable id: Long,
    ): ResponseEntity<ResData<SubtitleJobResponse?>> {
        val result = useCase.getJob(id)
        return ResData.success(result)
    }

    @Operation(summary = "자막 세그먼트 조회")
    @GetMapping("/{jobId}/segments")
    fun getSegments(
        @PathVariable jobId: Long,
    ): ResponseEntity<ResData<List<SubtitleSegmentResponse>>> {
        val result = useCase.getSegments(jobId)
        return ResData.success(result)
    }

    @Operation(summary = "자막 생성기 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<SubtitleGeneratorSummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
