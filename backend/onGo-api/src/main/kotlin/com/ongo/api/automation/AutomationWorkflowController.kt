package com.ongo.api.automation

import com.ongo.api.config.CurrentUser
import com.ongo.application.automation.AutomationWorkflowUseCase
import com.ongo.application.automation.dto.*
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "자동화 워크플로우", description = "멀티스텝 자동화 워크플로우 CRUD 및 토글")
@RestController
@RequestMapping("/api/v1/automation/workflows")
class AutomationWorkflowController(
    private val automationWorkflowUseCase: AutomationWorkflowUseCase,
) {

    @Operation(summary = "워크플로우 목록 조회")
    @GetMapping
    fun listWorkflows(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<WorkflowResponse>>> {
        val result = automationWorkflowUseCase.listWorkflows(userId)
        return ResData.success(result)
    }

    @Operation(summary = "워크플로우 상세 조회")
    @GetMapping("/{id}")
    fun getWorkflow(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<WorkflowResponse>> {
        val result = automationWorkflowUseCase.getWorkflow(userId, id)
        return ResData.success(result)
    }

    @Operation(summary = "워크플로우 생성")
    @PostMapping
    fun createWorkflow(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateWorkflowRequest,
    ): ResponseEntity<ResData<WorkflowResponse>> {
        val result = automationWorkflowUseCase.createWorkflow(userId, request)
        return ResData.success(result, "워크플로우가 생성되었습니다")
    }

    @Operation(summary = "워크플로우 수정")
    @PutMapping("/{id}")
    fun updateWorkflow(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateWorkflowRequest,
    ): ResponseEntity<ResData<WorkflowResponse>> {
        val result = automationWorkflowUseCase.updateWorkflow(userId, id, request)
        return ResData.success(result, "워크플로우가 수정되었습니다")
    }

    @Operation(summary = "워크플로우 삭제")
    @DeleteMapping("/{id}")
    fun deleteWorkflow(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        automationWorkflowUseCase.deleteWorkflow(userId, id)
        return ResData.success(null, "워크플로우가 삭제되었습니다")
    }

    @Operation(summary = "워크플로우 활성/비활성 토글")
    @PostMapping("/{id}/toggle")
    fun toggleWorkflow(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<WorkflowResponse>> {
        val result = automationWorkflowUseCase.toggleWorkflow(userId, id)
        return ResData.success(result)
    }

    @Operation(summary = "워크플로우 실행 이력 조회")
    @GetMapping("/{id}/executions")
    fun getExecutionHistory(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<List<WorkflowExecutionResponse>>> {
        val result = automationWorkflowUseCase.getExecutionHistory(userId, id)
        return ResData.success(result)
    }
}
