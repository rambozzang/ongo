package com.ongo.api.commentsummary

import com.ongo.application.commentsummary.CommentSummaryUseCase
import com.ongo.application.commentsummary.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "AI 댓글 요약", description = "AI 기반 댓글 요약 및 감정 분석")
@RestController
@RequestMapping("/api/v1/comment-summary")
class CommentSummaryController(
    private val useCase: CommentSummaryUseCase
) {

    @Operation(summary = "댓글 요약 결과 목록 조회")
    @GetMapping
    fun getResults(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) platform: String?,
    ): ResponseEntity<ResData<List<CommentSummaryResultResponse>>> {
        val result = useCase.getResults(userId, platform)
        return ResData.success(result)
    }

    @Operation(summary = "댓글 요약 분석 실행")
    @PostMapping("/analyze")
    fun analyze(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody body: Map<String, Long>,
    ): ResponseEntity<ResData<CommentSummaryResultResponse>> {
        val videoId = body["videoId"] ?: throw IllegalArgumentException("videoId required")
        val result = useCase.analyze(userId, videoId)
        return ResData.success(result)
    }

    @Operation(summary = "인기 댓글 목록 조회")
    @GetMapping("/{summaryId}/top-comments")
    fun getTopComments(
        @PathVariable summaryId: Long,
    ): ResponseEntity<ResData<List<TopCommentResponse>>> {
        val result = useCase.getTopComments(summaryId)
        return ResData.success(result)
    }

    @Operation(summary = "댓글 요약 통계")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<CommentSummarySummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
