package com.ongo.api.videoresizer

import com.ongo.application.videoresizer.VideoResizerUseCase
import com.ongo.application.videoresizer.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "영상 리사이저", description = "영상 비율 변환 및 리사이즈 작업 관리")
@RestController
@RequestMapping("/api/v1/video-resizer")
class VideoResizerController(
    private val videoResizerUseCase: VideoResizerUseCase
) {

    @Operation(summary = "리사이즈 작업 목록 조회")
    @GetMapping("/jobs")
    fun listResizeJobs(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<ResizeJobResponse>>> {
        val result = videoResizerUseCase.listResizeJobs(userId)
        return ResData.success(result)
    }

    @Operation(summary = "리사이즈 작업 생성")
    @PostMapping("/jobs")
    fun createResizeJob(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateResizeJobRequest,
    ): ResponseEntity<ResData<ResizeJobResponse>> {
        val result = videoResizerUseCase.createResizeJob(userId, request)
        return ResData.success(result, "리사이즈 작업이 생성되었습니다")
    }

    @Operation(summary = "리사이즈 작업 삭제")
    @DeleteMapping("/jobs/{id}")
    fun deleteResizeJob(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        videoResizerUseCase.deleteResizeJob(userId, id)
        return ResData.success(null, "리사이즈 작업이 삭제되었습니다")
    }
}
