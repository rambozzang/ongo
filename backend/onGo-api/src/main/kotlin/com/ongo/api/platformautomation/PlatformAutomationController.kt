package com.ongo.api.platformautomation

import com.ongo.application.platformautomation.PlatformAutomationUseCase
import com.ongo.application.platformautomation.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "플랫폼 자동화", description = "플랫폼별 자동 게시 규칙 관리")
@RestController
@RequestMapping("/api/v1/platform-automation")
class PlatformAutomationController(
    private val platformAutomationUseCase: PlatformAutomationUseCase
) {

    @Operation(summary = "자동화 규칙 목록 조회")
    @GetMapping
    fun getRules(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<AutomationRuleResponse>>> {
        val result = platformAutomationUseCase.getRules(userId)
        return ResData.success(result)
    }

    @Operation(summary = "자동화 규칙 생성")
    @PostMapping
    fun createRule(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateRuleRequest,
    ): ResponseEntity<ResData<AutomationRuleResponse>> {
        val result = platformAutomationUseCase.createRule(userId, request)
        return ResData.success(result, "자동화 규칙이 생성되었습니다")
    }

    @Operation(summary = "자동화 규칙 토글")
    @PutMapping("/{id}/toggle")
    fun toggleRule(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<AutomationRuleResponse>> {
        val result = platformAutomationUseCase.toggleRule(userId, id)
        return ResData.success(result)
    }

    @Operation(summary = "자동화 실행 로그")
    @GetMapping("/logs")
    fun getLogs(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) ruleId: Long?,
    ): ResponseEntity<ResData<List<AutomationLogResponse>>> {
        val result = platformAutomationUseCase.getLogs(userId, ruleId)
        return ResData.success(result)
    }

    @Operation(summary = "자동화 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<PlatformAutomationSummaryResponse>> {
        val result = platformAutomationUseCase.getSummary(userId)
        return ResData.success(result)
    }
}
