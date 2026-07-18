package com.ongo.api.ugc

import com.ongo.application.ugc.SubmissionUseCase
import com.ongo.application.ugc.dto.ReviewDecisionRequest
import com.ongo.application.ugc.dto.SubmissionDetailResponse
import com.ongo.application.ugc.dto.SubmissionListResponse
import com.ongo.application.ugc.dto.SubmissionResponse
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "UGC 검수 (브랜드)", description = "제출물 조회·수정요청·승인")
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/ugc")
class CampaignSubmissionController(
    private val submissionUseCase: SubmissionUseCase,
) {

    @Operation(summary = "제출물 목록 조회")
    @GetMapping("/campaigns/{campaignId}/submissions")
    fun list(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable campaignId: Long,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ResData<SubmissionListResponse>> =
        ResData.success(submissionUseCase.listSubmissions(userId, workspaceId, campaignId, status, page, size))

    @Operation(summary = "제출물 상세 (검수 이력 포함)")
    @GetMapping("/submissions/{submissionId}")
    fun detail(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable submissionId: Long,
    ): ResponseEntity<ResData<SubmissionDetailResponse>> =
        ResData.success(submissionUseCase.getSubmissionDetail(userId, workspaceId, submissionId))

    @Operation(summary = "수정 요청", description = "사유(comment)가 필수입니다.")
    @PostMapping("/submissions/{submissionId}/request-changes")
    fun requestChanges(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable submissionId: Long,
        @RequestBody request: ReviewDecisionRequest,
    ): ResponseEntity<ResData<SubmissionResponse>> =
        ResData.success(submissionUseCase.requestChanges(userId, workspaceId, submissionId, request), "수정 요청을 보냈습니다")

    @Operation(summary = "승인")
    @PostMapping("/submissions/{submissionId}/approve")
    fun approve(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable submissionId: Long,
        @RequestBody(required = false) request: ReviewDecisionRequest?,
    ): ResponseEntity<ResData<SubmissionResponse>> =
        ResData.success(
            submissionUseCase.approveSubmission(userId, workspaceId, submissionId, request ?: ReviewDecisionRequest()),
            "승인되었습니다",
        )
}
