package com.ongo.api.ugc

import com.ongo.application.ugc.CampaignAnalyticsUseCase
import com.ongo.application.ugc.dto.CampaignAnalyticsResponse
import com.ongo.application.ugc.dto.PostMetricResponse
import com.ongo.application.ugc.dto.RecordMetricRequest
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

@Tag(name = "UGC 성과 (브랜드)", description = "캠페인 성과 조회·지표 스냅샷 기록")
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/ugc")
class CampaignAnalyticsController(
    private val analyticsUseCase: CampaignAnalyticsUseCase,
) {

    @Operation(summary = "캠페인 성과 조회", description = "게시물별 최신 지표를 합산하고 마지막 동기화 시각을 반환합니다.")
    @GetMapping("/campaigns/{campaignId}/analytics")
    @RequiresPermission(Permission.CAMPAIGN_VIEW)
    fun analytics(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable campaignId: Long,
    ): ResponseEntity<ResData<CampaignAnalyticsResponse>> =
        ResData.success(analyticsUseCase.getAnalytics(userId, workspaceId, campaignId))

    @Operation(summary = "게시물 지표 기록/동기화", description = "지표 스냅샷을 기록합니다(동기화 스케줄러 진입점 대체).")
    @PostMapping("/campaign-posts/{campaignPostId}/metrics")
    @RequiresPermission(Permission.CAMPAIGN_MANAGE)
    fun recordMetric(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable campaignPostId: Long,
        @RequestBody request: RecordMetricRequest,
    ): ResponseEntity<ResData<PostMetricResponse>> =
        ResData.success(analyticsUseCase.recordMetric(userId, workspaceId, campaignPostId, request), "지표가 기록되었습니다")
}
