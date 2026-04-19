package com.ongo.api.comment

import com.ongo.api.config.CurrentUser
import com.ongo.application.ai.FaqClusteringUseCase
import com.ongo.application.ai.GenerateReplyUseCase
import com.ongo.application.comment.CommentAiReplyUseCase
import com.ongo.application.comment.CommentBatchUseCase
import com.ongo.application.comment.CommentEngagementUseCase
import com.ongo.application.comment.CommentSyncUseCase
import com.ongo.application.comment.CommentUseCase
import com.ongo.application.comment.CrisisDetectionUseCase
import com.ongo.application.comment.KeywordCloudUseCase
import com.ongo.application.comment.dto.*
import com.ongo.common.ResData
import com.ongo.common.enums.Platform
import com.ongo.domain.comment.CrisisDetectionConfig
import com.ongo.domain.comment.CrisisDetectionConfigRepository
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
    private val faqClusteringUseCase: FaqClusteringUseCase,
    private val generateReplyUseCase: GenerateReplyUseCase,
    private val commentAiReplyUseCase: CommentAiReplyUseCase,
    private val commentBatchUseCase: CommentBatchUseCase,
    private val crisisDetectionUseCase: CrisisDetectionUseCase,
    private val keywordCloudUseCase: KeywordCloudUseCase,
    private val crisisDetectionConfigRepository: CrisisDetectionConfigRepository,
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

    @Operation(summary = "댓글 고정/해제 토글", description = "지정된 댓글의 고정 상태를 토글합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "고정 상태 변경 성공"),
        ApiResponse(responseCode = "404", description = "댓글을 찾을 수 없음"),
    )
    @PutMapping("/{id}/pin")
    fun pinComment(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<CommentResponse>> {
        val result = commentEngagementUseCase.pinComment(userId, id)
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

    @Operation(summary = "감정 트렌드 조회", description = "기간별 댓글 감정 변화 추이를 조회합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "감정 트렌드 조회 성공"),
    )
    @GetMapping("/sentiment-trend")
    fun getSentimentTrend(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(defaultValue = "30") days: Int,
    ): ResponseEntity<ResData<SentimentTrendResponse>> {
        val result = commentUseCase.getSentimentTrend(userId, days)
        return ResData.success(result)
    }

    @Operation(summary = "FAQ 자동 분류", description = "AI를 활용하여 댓글에서 자주 묻는 질문을 자동으로 분류합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "FAQ 분류 성공"),
    )
    @GetMapping("/faq")
    fun getFaqClusters(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<FaqClusterResponse>> {
        val result = faqClusteringUseCase.execute(userId)
        return ResData.success(result)
    }

    @Operation(summary = "배치 AI 답변 초안", description = "여러 댓글에 대해 AI 답변 초안을 일괄 생성합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "배치 AI 답변 초안 생성 성공"),
    )
    @PostMapping("/ai-draft")
    fun batchAiDraft(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Valid @RequestBody request: BatchAiDraftRequest,
    ): ResponseEntity<ResData<BatchAiDraftResponse>> {
        val result = generateReplyUseCase.executeBatch(userId, request.commentIds, request.tone)
        return ResData.success(result, "AI 답변 초안이 생성되었습니다")
    }

    @Operation(summary = "AI 답변 후보 생성", description = "단일 댓글에 대해 3가지 톤의 AI 답변 후보를 생성합니다. 크레딧 2개 차감.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "AI 답변 후보 생성 성공"),
        ApiResponse(responseCode = "404", description = "댓글을 찾을 수 없음"),
    )
    @PostMapping("/ai-reply/generate")
    fun generateAiReplyCandidates(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Valid @RequestBody request: GenerateAiReplyRequest,
    ): ResponseEntity<ResData<AiReplyCandidatesResponse>> {
        val result = commentAiReplyUseCase.generateReplies(userId, request)
        return ResData.success(result, "AI 답변 후보가 생성되었습니다")
    }

    @Operation(summary = "일괄 답변", description = "선택한 댓글 목록에 동일한 내용으로 일괄 답변합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "일괄 답변 완료"),
    )
    @PostMapping("/batch/reply")
    fun batchReply(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Valid @RequestBody request: BatchReplyRequest,
    ): ResponseEntity<ResData<BatchResultResponse>> {
        val result = commentBatchUseCase.batchReply(userId, request)
        return ResData.success(result, "일괄 답변이 처리되었습니다")
    }

    @Operation(summary = "일괄 숨김", description = "선택한 댓글 목록을 일괄 숨깁니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "일괄 숨김 완료"),
    )
    @PostMapping("/batch/hide")
    fun batchHide(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Valid @RequestBody request: BatchHideRequest,
    ): ResponseEntity<ResData<BatchResultResponse>> {
        val result = commentBatchUseCase.batchHide(userId, request)
        return ResData.success(result, "일괄 숨김이 처리되었습니다")
    }

    @Operation(summary = "위기 감지 상태 조회", description = "부정 댓글 급증 여부를 감지하여 위기 상태를 반환합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "위기 감지 조회 성공"),
    )
    @GetMapping("/crisis-detection")
    fun getCrisisDetection(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<CrisisDetectionResponse>> {
        val config = crisisDetectionConfigRepository.findByUserId(userId)
            ?: CrisisDetectionConfig(userId = userId)
        val result = crisisDetectionUseCase.detect(userId)
        val response = CrisisDetectionResponse(
            isInCrisis = result.isInCrisis,
            currentNegativeCount = result.currentNegativeCount,
            previousNegativeCount = result.previousNegativeCount,
            changePercent = result.changePercent,
            topKeywords = result.topKeywords,
            windowHours = config.windowHours,
            thresholdPct = config.negativeThresholdPct,
        )
        return ResData.success(response)
    }

    @Operation(summary = "키워드 클라우드 조회", description = "댓글 내용에서 빈도 높은 키워드를 집계합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "키워드 클라우드 조회 성공"),
    )
    @GetMapping("/keyword-cloud")
    fun getKeywordCloud(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) videoId: Long?,
        @RequestParam(defaultValue = "30") days: Int,
    ): ResponseEntity<ResData<Map<String, Int>>> {
        val result = keywordCloudUseCase.getKeywordCloud(userId, videoId, days)
        return ResData.success(result)
    }
}
