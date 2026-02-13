package com.ongo.api.comment

import com.ongo.api.config.CurrentUser
import com.ongo.application.comment.CommentUseCase
import com.ongo.application.comment.dto.CommentListResponse
import com.ongo.application.comment.dto.CommentResponse
import com.ongo.application.comment.dto.ReplyCommentRequest
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "댓글 관리", description = "플랫폼 댓글 통합 조회, 답글, 삭제 관리")
@RestController
@RequestMapping("/api/v1/comments")
class CommentController(
    private val commentUseCase: CommentUseCase,
) {

    @Operation(summary = "댓글 목록 조회", description = "사용자의 댓글을 필터링하여 조회합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "댓글 목록 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
    )
    @GetMapping
    fun listComments(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) videoId: Long?,
        @RequestParam(required = false) platform: String?,
        @RequestParam(required = false) sentiment: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ResData<CommentListResponse>> {
        val result = commentUseCase.listComments(userId, videoId, platform, sentiment, page, size)
        return ResData.success(result)
    }

    @Operation(summary = "댓글 답글 작성", description = "지정된 댓글에 답글을 작성합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "답글 작성 성공"),
        ApiResponse(responseCode = "404", description = "댓글을 찾을 수 없음"),
    )
    @PostMapping("/{id}/reply")
    fun replyToComment(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: ReplyCommentRequest,
    ): ResponseEntity<ResData<CommentResponse>> {
        val result = commentUseCase.replyToComment(userId, id, request)
        return ResData.success(result, "답글이 작성되었습니다")
    }

    @Operation(summary = "댓글 삭제", description = "지정된 댓글을 삭제합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "댓글 삭제 성공"),
        ApiResponse(responseCode = "404", description = "댓글을 찾을 수 없음"),
    )
    @DeleteMapping("/{id}")
    fun deleteComment(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        commentUseCase.deleteComment(userId, id)
        return ResData.success(null, "댓글이 삭제되었습니다")
    }
}
