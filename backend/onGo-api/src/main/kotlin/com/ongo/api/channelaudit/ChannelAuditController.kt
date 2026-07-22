package com.ongo.api.channelaudit

import com.ongo.application.channelaudit.ChannelAuditListResponse
import com.ongo.application.channelaudit.ChannelAuditResponse
import com.ongo.application.channelaudit.ChannelAuditUseCase
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "채널 오디트", description = "AI 채널 종합 분석 — 강점/약점/액션 아이템 (15크레딧)")
@RestController
@RequestMapping("/api/v1/reports/channel-audit")
class ChannelAuditController(
    private val channelAuditUseCase: ChannelAuditUseCase,
) {

    @Operation(
        summary = "AI 채널 오디트 생성 (15크레딧)",
        description = "채널 전체를 AI가 분석하여 강점/약점/구체적 액션 아이템을 제공합니다."
    )
    @PostMapping
    fun generateAudit(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<ChannelAuditResponse>> {
        val result = channelAuditUseCase.generateAudit(userId)
        return ResData.success(result, "채널 오디트가 생성되었습니다")
    }

    @Operation(
        summary = "채널 오디트 목록 조회",
        description = "내 채널 오디트 리포트 목록을 페이지네이션으로 조회합니다."
    )
    @GetMapping
    fun getAudits(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): ResponseEntity<ResData<ChannelAuditListResponse>> {
        val result = channelAuditUseCase.getAudits(userId, page, size)
        return ResData.success(result)
    }

    @Operation(
        summary = "채널 오디트 상세 조회",
        description = "특정 채널 오디트 리포트의 상세 내용을 조회합니다."
    )
    @GetMapping("/{id}")
    fun getAuditDetail(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<ChannelAuditResponse>> {
        val result = channelAuditUseCase.getAuditDetail(userId, id)
        return ResData.success(result)
    }
}
