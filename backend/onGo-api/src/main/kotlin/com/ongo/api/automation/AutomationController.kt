package com.ongo.api.automation

import com.ongo.api.config.CurrentUser
import com.ongo.application.automation.AutomationUseCase
import com.ongo.application.automation.AutomationWorkflowUseCase
import com.ongo.application.automation.dto.*
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "자동화 규칙", description = "자동화 규칙 CRUD 및 토글")
@RestController
@RequestMapping("/api/v1/automation")
class AutomationController(
    private val automationUseCase: AutomationUseCase,
    private val workflowUseCase: AutomationWorkflowUseCase,
) {

    @Operation(summary = "자동화 규칙 목록 조회")
    @GetMapping("/rules")
    fun listRules(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<AutomationRuleResponse>>> {
        val result = automationUseCase.listRules(userId)
        return ResData.success(result)
    }

    @Operation(summary = "자동화 규칙 생성")
    @PostMapping("/rules")
    fun createRule(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateAutomationRuleRequest,
    ): ResponseEntity<ResData<AutomationRuleResponse>> {
        val result = automationUseCase.createRule(userId, request)
        return ResData.success(result, "자동화 규칙이 생성되었습니다")
    }

    @Operation(summary = "자동화 규칙 수정")
    @PutMapping("/rules/{id}")
    fun updateRule(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateAutomationRuleRequest,
    ): ResponseEntity<ResData<AutomationRuleResponse>> {
        val result = automationUseCase.updateRule(userId, id, request)
        return ResData.success(result, "자동화 규칙이 수정되었습니다")
    }

    @Operation(summary = "자동화 규칙 삭제")
    @DeleteMapping("/rules/{id}")
    fun deleteRule(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        automationUseCase.deleteRule(userId, id)
        return ResData.success(null, "자동화 규칙이 삭제되었습니다")
    }

    @Operation(summary = "스마트 트리거 템플릿 조회", description = "성능 기반 스마트 트리거 템플릿 목록을 조회합니다 (조회수 마일스톤, 바이럴 감지, 참여율 하락)")
    @GetMapping("/rules/smart-templates")
    fun getSmartTriggerTemplates(): ResponseEntity<ResData<List<SmartTriggerTemplate>>> {
        return ResData.success(automationUseCase.getSmartTriggerTemplates())
    }

    @Operation(summary = "자동화 규칙 활성/비활성 토글")
    @PutMapping("/rules/{id}/toggle")
    fun toggleRule(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<AutomationRuleResponse>> {
        val result = automationUseCase.toggleRule(userId, id)
        return ResData.success(result)
    }

    // ─── Workflow endpoints ───

    @Operation(summary = "워크플로우 목록 조회")
    @GetMapping("/workflows")
    fun listWorkflows(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<WorkflowResponse>>> {
        val result = workflowUseCase.listWorkflows(userId)
        return ResData.success(result)
    }

    @Operation(summary = "워크플로우 상세 조회")
    @GetMapping("/workflows/{id}")
    fun getWorkflow(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<WorkflowResponse>> {
        val result = workflowUseCase.getWorkflow(userId, id)
        return ResData.success(result)
    }

    @Operation(summary = "워크플로우 생성")
    @PostMapping("/workflows")
    fun createWorkflow(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateWorkflowRequest,
    ): ResponseEntity<ResData<WorkflowResponse>> {
        val result = workflowUseCase.createWorkflow(userId, request)
        return ResData.success(result, "워크플로우가 생성되었습니다")
    }

    @Operation(summary = "워크플로우 수정")
    @PutMapping("/workflows/{id}")
    fun updateWorkflow(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateWorkflowRequest,
    ): ResponseEntity<ResData<WorkflowResponse>> {
        val result = workflowUseCase.updateWorkflow(userId, id, request)
        return ResData.success(result, "워크플로우가 수정되었습니다")
    }

    @Operation(summary = "워크플로우 삭제")
    @DeleteMapping("/workflows/{id}")
    fun deleteWorkflow(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        workflowUseCase.deleteWorkflow(userId, id)
        return ResData.success(null, "워크플로우가 삭제되었습니다")
    }

    @Operation(summary = "워크플로우 활성/비활성 토글")
    @PostMapping("/workflows/{id}/toggle")
    fun toggleWorkflow(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<WorkflowResponse>> {
        val result = workflowUseCase.toggleWorkflow(userId, id)
        return ResData.success(result)
    }

    @Operation(summary = "워크플로우 실행 이력 조회")
    @GetMapping("/workflows/{id}/history")
    fun getWorkflowHistory(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<List<WorkflowExecutionResponse>>> {
        val result = workflowUseCase.getExecutionHistory(userId, id)
        return ResData.success(result)
    }
}
