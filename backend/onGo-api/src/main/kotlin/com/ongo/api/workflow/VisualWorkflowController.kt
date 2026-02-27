package com.ongo.api.workflow

import com.ongo.application.workflow.VisualWorkflowUseCase
import com.ongo.application.workflow.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "비주얼 워크플로우", description = "비주얼 워크플로우 빌더")
@RestController
@RequestMapping("/api/v1/workflows")
class VisualWorkflowController(
    private val visualWorkflowUseCase: VisualWorkflowUseCase
) {

    @Operation(summary = "워크플로우 목록 조회")
    @GetMapping
    fun listWorkflows(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<WorkflowResponse>>> {
        val result = visualWorkflowUseCase.listWorkflows(userId)
        return ResData.success(result)
    }

    @Operation(summary = "워크플로우 상세 조회")
    @GetMapping("/{id}")
    fun getWorkflow(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<WorkflowResponse>> {
        val result = visualWorkflowUseCase.getWorkflow(userId, id)
        return ResData.success(result)
    }

    @Operation(summary = "워크플로우 생성")
    @PostMapping
    fun createWorkflow(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateWorkflowRequest,
    ): ResponseEntity<ResData<WorkflowResponse>> {
        val result = visualWorkflowUseCase.createWorkflow(userId, request)
        return ResData.success(result, "워크플로우가 생성되었습니다")
    }

    @Operation(summary = "워크플로우 수정")
    @PutMapping("/{id}")
    fun updateWorkflow(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateWorkflowRequest,
    ): ResponseEntity<ResData<WorkflowResponse>> {
        val result = visualWorkflowUseCase.updateWorkflow(userId, id, request)
        return ResData.success(result)
    }

    @Operation(summary = "워크플로우 삭제")
    @DeleteMapping("/{id}")
    fun deleteWorkflow(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        visualWorkflowUseCase.deleteWorkflow(userId, id)
        return ResData.success(null, "워크플로우가 삭제되었습니다")
    }

    @Operation(summary = "워크플로우 활성/비활성 토글")
    @PostMapping("/{id}/toggle")
    fun toggleWorkflow(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<WorkflowResponse>> {
        val result = visualWorkflowUseCase.toggleWorkflow(userId, id)
        return ResData.success(result, "워크플로우 상태가 변경되었습니다")
    }

    @Operation(summary = "워크플로우 테스트 실행")
    @PostMapping("/{id}/test")
    fun testWorkflow(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<WorkflowTestResponse>> {
        val result = visualWorkflowUseCase.testWorkflow(userId, id)
        return ResData.success(result, "워크플로우 테스트가 실행되었습니다")
    }

    @Operation(summary = "워크플로우 실행 이력 조회")
    @GetMapping("/{id}/executions")
    fun getExecutions(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<List<WorkflowExecutionResponse>>> {
        val result = visualWorkflowUseCase.getExecutions(userId, id)
        return ResData.success(result)
    }
}
