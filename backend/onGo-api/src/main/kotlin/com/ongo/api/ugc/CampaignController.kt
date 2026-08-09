package com.ongo.api.ugc

import com.ongo.application.ugc.CampaignUseCase
import com.ongo.application.ugc.dto.CampaignDetailResponse
import com.ongo.application.ugc.dto.CampaignListResponse
import com.ongo.application.ugc.dto.CreateCampaignRequest
import com.ongo.application.ugc.dto.PlaybookResponse
import com.ongo.application.ugc.dto.UpdateCampaignRequest
import com.ongo.application.ugc.dto.UpsertPlaybookRequest
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import com.ongo.common.annotation.RequiresPermission
import com.ongo.common.enums.Permission
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "UGC 캠페인", description = "브랜드 UGC 캠페인 생성·공개·플레이북 관리")
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/ugc/campaigns")
class CampaignController(
    private val campaignUseCase: CampaignUseCase,
) {

    @Operation(summary = "캠페인 목록 조회")
    @RequiresPermission(Permission.CAMPAIGN_VIEW)
    @GetMapping
    fun list(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) query: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ResData<CampaignListResponse>> =
        ResData.success(campaignUseCase.listCampaigns(userId, workspaceId, status, query, page, size))

    @Operation(summary = "캠페인 상세 조회")
    @RequiresPermission(Permission.CAMPAIGN_VIEW)
    @GetMapping("/{campaignId}")
    fun get(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable campaignId: Long,
    ): ResponseEntity<ResData<CampaignDetailResponse>> =
        ResData.success(campaignUseCase.getCampaign(userId, workspaceId, campaignId))

    @Operation(summary = "캠페인 생성")
    @RequiresPermission(Permission.CAMPAIGN_MANAGE)
    @PostMapping
    fun create(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @RequestBody request: CreateCampaignRequest,
    ): ResponseEntity<ResData<CampaignDetailResponse>> =
        ResData.success(campaignUseCase.createCampaign(userId, workspaceId, request), "캠페인이 생성되었습니다")

    @Operation(summary = "캠페인 수정 (DRAFT 상태만)")
    @RequiresPermission(Permission.CAMPAIGN_MANAGE)
    @PatchMapping("/{campaignId}")
    fun update(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable campaignId: Long,
        @RequestBody request: UpdateCampaignRequest,
    ): ResponseEntity<ResData<CampaignDetailResponse>> =
        ResData.success(campaignUseCase.updateCampaign(userId, workspaceId, campaignId, request))

    @Operation(summary = "캠페인 공개", description = "DRAFT → RECRUITING. 활성 플레이북과 기간이 필요합니다.")
    @RequiresPermission(Permission.CAMPAIGN_MANAGE)
    @PostMapping("/{campaignId}/publish")
    fun publish(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable campaignId: Long,
    ): ResponseEntity<ResData<CampaignDetailResponse>> =
        ResData.success(campaignUseCase.publishCampaign(userId, workspaceId, campaignId), "캠페인이 공개되었습니다")

    @Operation(summary = "캠페인 일시중지")
    @RequiresPermission(Permission.CAMPAIGN_MANAGE)
    @PostMapping("/{campaignId}/pause")
    fun pause(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable campaignId: Long,
    ): ResponseEntity<ResData<CampaignDetailResponse>> =
        ResData.success(campaignUseCase.pauseCampaign(userId, workspaceId, campaignId), "캠페인이 일시중지되었습니다")

    @Operation(summary = "캠페인 종료")
    @RequiresPermission(Permission.CAMPAIGN_MANAGE)
    @PostMapping("/{campaignId}/complete")
    fun complete(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable campaignId: Long,
    ): ResponseEntity<ResData<CampaignDetailResponse>> =
        ResData.success(campaignUseCase.completeCampaign(userId, workspaceId, campaignId), "캠페인이 종료되었습니다")

    @Operation(summary = "플레이북 저장", description = "캠페인당 활성 플레이북 1개를 upsert합니다.")
    @RequiresPermission(Permission.CAMPAIGN_MANAGE)
    @PutMapping("/{campaignId}/playbook")
    fun upsertPlaybook(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable campaignId: Long,
        @RequestBody request: UpsertPlaybookRequest,
    ): ResponseEntity<ResData<PlaybookResponse>> =
        ResData.success(campaignUseCase.upsertPlaybook(userId, workspaceId, campaignId, request), "플레이북이 저장되었습니다")
}
