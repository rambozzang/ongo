package com.ongo.api.smartreply

import com.ongo.application.smartreply.SmartReplyUseCase
import com.ongo.application.smartreply.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "스마트 리플라이", description = "AI 기반 댓글 자동 답변 관리")
@RestController
@RequestMapping("/api/v1/smart-reply")
class SmartReplyController(
    private val smartReplyUseCase: SmartReplyUseCase
) {

    @Operation(summary = "답변 제안 조회")
    @GetMapping("/suggestions")
    fun getSuggestions(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<SmartReplySuggestionResponse>>> {
        val result = smartReplyUseCase.getSuggestions(userId)
        return ResData.success(result)
    }

    @Operation(summary = "답변 발송")
    @PostMapping("/send")
    fun sendReply(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: SendReplyRequest,
    ): ResponseEntity<ResData<Nothing?>> {
        smartReplyUseCase.sendReply(userId, request)
        return ResData.success(null, "답변이 발송되었습니다")
    }

    @Operation(summary = "답변 제안 무시")
    @DeleteMapping("/suggestions/{id}")
    fun dismissSuggestion(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: String,
    ): ResponseEntity<ResData<Nothing?>> {
        smartReplyUseCase.dismissSuggestion(userId, id)
        return ResData.success(null, "답변 제안이 무시되었습니다")
    }

    @Operation(summary = "답변 규칙 조회")
    @GetMapping("/rules")
    fun getRules(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<SmartReplyRuleResponse>>> {
        val result = smartReplyUseCase.getRules(userId)
        return ResData.success(result)
    }

    @Operation(summary = "답변 규칙 생성")
    @PostMapping("/rules")
    fun createRule(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateRuleRequest,
    ): ResponseEntity<ResData<SmartReplyRuleResponse>> {
        val result = smartReplyUseCase.createRule(userId, request)
        return ResData.success(result, "답변 규칙이 생성되었습니다")
    }

    @Operation(summary = "답변 규칙 수정")
    @PutMapping("/rules/{id}")
    fun updateRule(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateRuleRequest,
    ): ResponseEntity<ResData<SmartReplyRuleResponse>> {
        val result = smartReplyUseCase.updateRule(userId, id, request)
        return ResData.success(result, "답변 규칙이 수정되었습니다")
    }

    @Operation(summary = "답변 규칙 삭제")
    @DeleteMapping("/rules/{id}")
    fun deleteRule(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        smartReplyUseCase.deleteRule(userId, id)
        return ResData.success(null, "답변 규칙이 삭제되었습니다")
    }

    @Operation(summary = "스마트 리플라이 통계 조회")
    @GetMapping("/stats")
    fun getStats(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<SmartReplyStatsResponse>> {
        val result = smartReplyUseCase.getStats(userId)
        return ResData.success(result)
    }

    @Operation(summary = "스마트 리플라이 설정 조회")
    @GetMapping("/config")
    fun getConfig(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<SmartReplyConfigResponse>> {
        val result = smartReplyUseCase.getConfig(userId)
        return ResData.success(result)
    }

    @Operation(summary = "스마트 리플라이 설정 수정")
    @PutMapping("/config")
    fun updateConfig(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: UpdateConfigRequest,
    ): ResponseEntity<ResData<SmartReplyConfigResponse>> {
        val result = smartReplyUseCase.updateConfig(userId, request)
        return ResData.success(result, "설정이 수정되었습니다")
    }
}
