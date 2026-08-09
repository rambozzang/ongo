package com.ongo.api.ugc

import com.ongo.application.ugc.CampaignPublishingUseCase
import com.ongo.application.ugc.dto.CampaignPostListResponse
import com.ongo.application.ugc.dto.PublishRequest
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import com.ongo.common.annotation.RequiresPermission
import com.ongo.common.enums.Permission
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "UGC 게시 (브랜드)", description = "승인 제출물 멀티 SNS 게시·게시물 상태 조회")
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/ugc")
class CampaignPublishingController(
    private val publishingUseCase: CampaignPublishingUseCase,
) {

    @Operation(summary = "제출물 게시", description = "승인된 제출물의 영상을 선택 플랫폼에 게시합니다. 재시도해도 중복 게시되지 않습니다.")
    @PostMapping("/submissions/{submissionId}/publish")
    @RequiresPermission(Permission.CAMPAIGN_MANAGE)
    fun publish(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable submissionId: Long,
        @RequestBody request: PublishRequest,
    ): ResponseEntity<ResData<CampaignPostListResponse>> =
        ResData.success(publishingUseCase.publishSubmission(userId, workspaceId, submissionId, request), "게시를 시작했습니다")

    @Operation(summary = "캠페인 게시물 목록")
    @GetMapping("/campaigns/{campaignId}/posts")
    @RequiresPermission(Permission.CAMPAIGN_VIEW)
    fun listPosts(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable campaignId: Long,
    ): ResponseEntity<ResData<CampaignPostListResponse>> =
        ResData.success(publishingUseCase.listCampaignPosts(userId, workspaceId, campaignId))
}
