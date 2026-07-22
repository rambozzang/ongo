package com.ongo.api.ugc

import com.ongo.application.ugc.ParticipationUseCase
import com.ongo.application.ugc.dto.ApplicationListResponse
import com.ongo.application.ugc.dto.ApplicationResponse
import com.ongo.application.ugc.dto.CreateInviteRequest
import com.ongo.application.ugc.dto.InviteResponse
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

@Tag(name = "UGC 모집·참여 (브랜드)", description = "초대 링크 발급, 지원자 목록·수락·거절")
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/ugc")
class CampaignParticipationController(
    private val participationUseCase: ParticipationUseCase,
) {

    @Operation(summary = "초대 링크 생성")
    @PostMapping("/campaigns/{campaignId}/invites")
    fun createInvite(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable campaignId: Long,
        @RequestBody request: CreateInviteRequest,
    ): ResponseEntity<ResData<InviteResponse>> =
        ResData.success(
            participationUseCase.createInvite(userId, workspaceId, campaignId, request),
            "초대 링크가 생성되었습니다",
        )

    @Operation(summary = "지원자 목록 조회")
    @GetMapping("/campaigns/{campaignId}/applications")
    fun listApplications(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable campaignId: Long,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ResData<ApplicationListResponse>> =
        ResData.success(participationUseCase.listApplications(userId, workspaceId, campaignId, status, page, size))

    @Operation(summary = "지원 수락", description = "지원을 수락하고 참여자를 생성합니다(원자적).")
    @PostMapping("/applications/{applicationId}/accept")
    fun accept(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable applicationId: Long,
    ): ResponseEntity<ResData<ApplicationResponse>> =
        ResData.success(participationUseCase.acceptApplication(userId, workspaceId, applicationId), "지원을 수락했습니다")

    @Operation(summary = "지원 거절")
    @PostMapping("/applications/{applicationId}/reject")
    fun reject(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable applicationId: Long,
    ): ResponseEntity<ResData<ApplicationResponse>> =
        ResData.success(participationUseCase.rejectApplication(userId, workspaceId, applicationId), "지원을 거절했습니다")
}
