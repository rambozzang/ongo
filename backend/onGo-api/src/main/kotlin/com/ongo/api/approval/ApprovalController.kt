package com.ongo.api.approval

import com.ongo.application.approval.ApprovalChainUseCase
import com.ongo.application.approval.ApprovalUseCase
import com.ongo.application.approval.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "승인 워크플로우", description = "콘텐츠 승인 요청, 상태 변경, 코멘트 관리, 멀티스텝 체인")
@RestController
@RequestMapping("/api/v1/approvals")
class ApprovalController(
    private val approvalUseCase: ApprovalUseCase,
    private val approvalChainUseCase: ApprovalChainUseCase,
) {

    @Operation(summary = "승인 목록 조회", description = "사용자의 승인 요청 목록을 조회합니다. status 파라미터로 필터링 가능합니다.")
    @GetMapping
    fun listApprovals(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) status: String?,
    ): ResponseEntity<ResData<ApprovalListResponse>> {
        return ResData.success(approvalUseCase.listApprovals(userId, status))
    }

    @Operation(summary = "승인 상세 조회")
    @GetMapping("/{id}")
    fun getApproval(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<ApprovalResponse>> {
        return ResData.success(approvalUseCase.getApproval(userId, id))
    }

    @Operation(summary = "승인 요청 생성", description = "새로운 콘텐츠 승인 요청을 생성합니다")
    @PostMapping
    fun submitForApproval(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Valid @RequestBody request: CreateApprovalRequest,
    ): ResponseEntity<ResData<ApprovalResponse>> {
        val result = approvalUseCase.submitForApproval(userId, request)
        return ResData.success(result, "승인 요청이 생성되었습니다")
    }

    @Operation(summary = "승인 상태 변경", description = "승인 요청의 상태를 변경합니다 (승인/거절/수정요청)")
    @PutMapping("/{id}/status")
    fun updateStatus(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateApprovalStatusRequest,
    ): ResponseEntity<ResData<ApprovalResponse>> {
        val result = approvalUseCase.updateStatus(userId, id, request)
        return ResData.success(result, "승인 상태가 변경되었습니다")
    }

    @Operation(summary = "코멘트 목록 조회", description = "승인 요청에 달린 코멘트 목록을 조회합니다")
    @GetMapping("/{id}/comments")
    fun getComments(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<List<ApprovalCommentResponse>>> {
        return ResData.success(approvalUseCase.getComments(userId, id))
    }

    @Operation(summary = "코멘트 추가", description = "승인 요청에 코멘트를 추가합니다")
    @PostMapping("/{id}/comments")
    fun addComment(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: CreateApprovalCommentRequest,
    ): ResponseEntity<ResData<ApprovalCommentResponse>> {
        val result = approvalUseCase.addComment(userId, "사용자", id, request)
        return ResData.success(result, "코멘트가 추가되었습니다")
    }

    // ─── Approval Chain endpoints ─────────────────────────────────

    @Operation(summary = "승인 체인 조회", description = "멀티스텝 승인 체인의 단계 목록을 조회합니다")
    @GetMapping("/{id}/chain")
    fun getChainSteps(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<List<ApprovalChainResponse>>> {
        return ResData.success(approvalChainUseCase.getChainSteps(userId, id))
    }

    @Operation(summary = "승인 체인 생성", description = "멀티스텝 승인 체인을 설정합니다. 기존 체인이 있으면 교체됩니다.")
    @PostMapping("/{id}/chain")
    fun createChain(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: CreateApprovalChainRequest,
    ): ResponseEntity<ResData<List<ApprovalChainResponse>>> {
        val result = approvalChainUseCase.createChain(userId, id, request)
        return ResData.success(result, "승인 체인이 생성되었습니다")
    }

    @Operation(summary = "승인 단계 승인", description = "현재 단계를 승인하고 다음 단계를 활성화합니다")
    @PostMapping("/{id}/chain/{stepId}/approve")
    fun approveStep(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @PathVariable stepId: Long,
        @Valid @RequestBody request: ApproveStepRequest,
    ): ResponseEntity<ResData<ApprovalChainResponse>> {
        val result = approvalChainUseCase.approveStep(userId, id, stepId, request.comment)
        return ResData.success(result, "승인 단계가 승인되었습니다")
    }

    @Operation(summary = "승인 단계 거절", description = "현재 단계를 거절하고 전체 승인을 거절합니다")
    @PostMapping("/{id}/chain/{stepId}/reject")
    fun rejectStep(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @PathVariable stepId: Long,
        @Valid @RequestBody request: ApproveStepRequest,
    ): ResponseEntity<ResData<ApprovalChainResponse>> {
        val result = approvalChainUseCase.rejectStep(userId, id, stepId, request.comment)
        return ResData.success(result, "승인 단계가 거절되었습니다")
    }

    @Operation(summary = "SLA 상태 조회", description = "승인 체인의 SLA 상태를 조회합니다")
    @GetMapping("/{id}/sla")
    fun getSlaStatus(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<List<ApprovalChainSlaResponse>>> {
        return ResData.success(approvalChainUseCase.getSlaStatus(userId, id))
    }
}
