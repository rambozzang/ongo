package com.ongo.api.ugc

import com.ongo.application.ugc.AuditUseCase
import com.ongo.application.ugc.dto.AuditEventListResponse
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "UGC 감사 로그 (운영자)", description = "캠페인 주요 상태·정산 변경 이력 조회")
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/ugc")
class CampaignAuditController(
    private val auditUseCase: AuditUseCase,
) {

    @Operation(summary = "캠페인 감사 로그 조회")
    @GetMapping("/campaigns/{campaignId}/audit-events")
    fun list(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable campaignId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ResData<AuditEventListResponse>> =
        ResData.success(auditUseCase.listByCampaign(userId, workspaceId, campaignId, page, size))
}
