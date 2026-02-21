package com.ongo.api.workspace

import com.ongo.application.workspace.WorkspaceUseCase
import com.ongo.application.workspace.dto.CreateWorkspaceRequest
import com.ongo.application.workspace.dto.UpdateWorkspaceRequest
import com.ongo.application.workspace.dto.WorkspaceResponse
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "워크스페이스", description = "워크스페이스 CRUD 및 전환")
@RestController
@RequestMapping("/api/v1/workspaces")
class WorkspaceController(
    private val workspaceUseCase: WorkspaceUseCase,
) {

    @Operation(summary = "워크스페이스 목록 조회")
    @GetMapping
    fun listWorkspaces(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<WorkspaceResponse>>> =
        ResData.success(workspaceUseCase.listWorkspaces(userId))

    @Operation(summary = "워크스페이스 상세 조회")
    @GetMapping("/{id}")
    fun getWorkspace(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<WorkspaceResponse>> =
        ResData.success(workspaceUseCase.getWorkspace(userId, id))

    @Operation(summary = "워크스페이스 생성")
    @PostMapping
    fun createWorkspace(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateWorkspaceRequest,
    ): ResponseEntity<ResData<WorkspaceResponse>> =
        ResData.success(workspaceUseCase.createWorkspace(userId, request))

    @Operation(summary = "워크스페이스 수정")
    @PutMapping("/{id}")
    fun updateWorkspace(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateWorkspaceRequest,
    ): ResponseEntity<ResData<WorkspaceResponse>> =
        ResData.success(workspaceUseCase.updateWorkspace(userId, id, request))

    @Operation(summary = "워크스페이스 삭제")
    @DeleteMapping("/{id}")
    fun deleteWorkspace(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        workspaceUseCase.deleteWorkspace(userId, id)
        return ResData.success(null)
    }
}
