package com.ongo.api.comment

import com.ongo.api.config.CurrentUser
import com.ongo.application.comment.CommentEngagementUseCase
import com.ongo.application.comment.CommentSyncUseCase
import com.ongo.application.comment.CommentUseCase
import com.ongo.application.comment.dto.*
import com.ongo.common.ResData
import com.ongo.common.enums.Platform
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@Tag(name = "댓글 관리", description = "크로스 플랫폼 댓글 통합 조회, 동기화, 답글, 삭제 관리")
@RestController
@RequestMapping("/api/v1/comments")
class CommentController(
    private val commentUseCase: CommentUseCase,
    private val commentSyncUseCase: CommentSyncUseCase,
    private val commentEngagementUseCase: CommentEngagementUseCase,
) {

    @Operation(summary = "댓글 목록 조회", description = "사용자의 댓글을 복합 필터링하여 조회합니다.")
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
        @RequestParam(required = false) searchText: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: LocalDateTime?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ResData<CommentListResponse>> {
        val result = commentUseCase.listComments(
            userId, videoId, platform, sentiment, searchText, startDate, endDate, page, size,
        )
        return ResData.success(result)
    }

    @Operation(summary = "전체 댓글 동기화", description = "연결된 모든 플랫폼에서 댓글을 가져와 동기화합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "동기화 성공"),
    )
    @PostMapping("/sync")
    fun syncAllComments(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<CommentSyncResult>> {
        val result = commentSyncUseCase.syncAllComments(userId)
        return ResData.success(result, "댓글 동기화가 완료되었습니다")
    }

    @Operation(summary = "특정 영상 댓글 동기화", description = "특정 영상의 댓글을 플랫폼에서 가져와 동기화합니다.")
    @PostMapping("/sync/video/{videoId}")
    fun syncVideoComments(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable videoId: Long,
        @RequestParam platform: String,
        @RequestParam platformVideoId: String,
    ): ResponseEntity<ResData<CommentSyncResult>> {
        val platformEnum = Platform.valueOf(platform.uppercase())
        val (synced, newCount) = commentSyncUseCase.syncVideoComments(
            userId = userId,
            videoId = videoId,
            platform = platformEnum,
            platformVideoId = platformVideoId,
        )
        return ResData.success(
            CommentSyncResult(synced, newCount, emptyList()),
            "영상 댓글 동기화가 완료되었습니다",
        )
    }

    @Operation(summary = "댓글 답글 작성", description = "지정된 댓글에 답글을 작성합니다. 플랫폼에도 전송됩니다.")
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
        val result = commentEngagementUseCase.replyToComment(userId, id, request.content)
        return ResData.success(result, "답글이 작성되었습니다")
    }

    @Operation(summary = "댓글 삭제", description = "지정된 댓글을 삭제합니다. 플랫폼에서도 삭제됩니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "댓글 삭제 성공"),
        ApiResponse(responseCode = "404", description = "댓글을 찾을 수 없음"),
    )
    @DeleteMapping("/{id}")
    fun deleteComment(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        commentEngagementUseCase.deleteComment(userId, id)
        return ResData.success(null, "댓글이 삭제되었습니다")
    }

    @Operation(summary = "댓글 숨김 토글", description = "지정된 댓글의 숨김 상태를 토글합니다.")
    @PutMapping("/{id}/hide")
    fun hideComment(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<CommentResponse>> {
        val result = commentEngagementUseCase.hideComment(userId, id)
        return ResData.success(result)
    }

    @Operation(summary = "플랫폼별 capabilities 조회", description = "연결된 플랫폼별 댓글 기능 지원 현황을 조회합니다.")
    @GetMapping("/capabilities")
    fun getCapabilities(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<Map<String, CommentCapabilitiesDto>>> {
        val result = commentEngagementUseCase.getCapabilitiesMap(userId)
        return ResData.success(result)
    }
}
