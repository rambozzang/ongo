package com.ongo.api.audiencesegment

import com.ongo.application.audiencesegment.AudienceSegmentUseCase
import com.ongo.application.audiencesegment.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "오디언스 세분화", description = "AI 기반 오디언스 세그먼트 분석 및 인사이트")
@RestController
@RequestMapping("/api/v1/audience-segments")
class AudienceSegmentController(
    private val audienceSegmentUseCase: AudienceSegmentUseCase
) {

    @Operation(summary = "세그먼트 목록 조회")
    @GetMapping
    fun getAll(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<AudienceSegmentResponse>>> {
        val result = audienceSegmentUseCase.getAll(userId)
        return ResData.success(result)
    }

    @Operation(summary = "세그먼트 상세 조회")
    @GetMapping("/{id}")
    fun getById(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<AudienceSegmentResponse>> {
        val result = audienceSegmentUseCase.getById(userId, id)
        return ResData.success(result)
    }

    @Operation(summary = "세그먼트 생성")
    @PostMapping
    fun create(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateSegmentRequest,
    ): ResponseEntity<ResData<AudienceSegmentResponse>> {
        val result = audienceSegmentUseCase.create(userId, request)
        return ResData.success(result, "세그먼트가 생성되었습니다")
    }

    @Operation(summary = "세그먼트 삭제")
    @DeleteMapping("/{id}")
    fun delete(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        audienceSegmentUseCase.delete(userId, id)
        return ResData.success(null, "세그먼트가 삭제되었습니다")
    }

    @Operation(summary = "세그먼트 AI 인사이트 조회")
    @GetMapping("/{segmentId}/insight")
    fun getInsight(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable segmentId: Long,
    ): ResponseEntity<ResData<SegmentInsightResponse>> {
        val result = audienceSegmentUseCase.getInsight(userId, segmentId)
        return ResData.success(result)
    }
}
