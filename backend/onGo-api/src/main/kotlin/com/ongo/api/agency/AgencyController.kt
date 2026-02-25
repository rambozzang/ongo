package com.ongo.api.agency

import com.ongo.application.agency.AgencyUseCase
import com.ongo.application.agency.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "에이전시", description = "에이전시 워크스페이스 및 크리에이터 관리")
@RestController
@RequestMapping("/api/v1/agency")
class AgencyController(
    private val agencyUseCase: AgencyUseCase
) {

    @Operation(summary = "워크스페이스 목록 조회")
    @GetMapping("/workspaces")
    fun listWorkspaces(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<AgencyWorkspaceResponse>>> {
        val result = agencyUseCase.listWorkspaces(userId)
        return ResData.success(result)
    }

    @Operation(summary = "워크스페이스 상세 조회")
    @GetMapping("/workspaces/{id}")
    fun getWorkspace(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<AgencyWorkspaceResponse>> {
        val result = agencyUseCase.getWorkspace(userId, id)
        return ResData.success(result)
    }

    @Operation(summary = "워크스페이스 생성")
    @PostMapping("/workspaces")
    fun createWorkspace(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateWorkspaceRequest,
    ): ResponseEntity<ResData<AgencyWorkspaceResponse>> {
        val result = agencyUseCase.createWorkspace(userId, request)
        return ResData.success(result, "워크스페이스가 생성되었습니다")
    }

    @Operation(summary = "워크스페이스 수정")
    @PutMapping("/workspaces/{id}")
    fun updateWorkspace(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateWorkspaceRequest,
    ): ResponseEntity<ResData<AgencyWorkspaceResponse>> {
        val result = agencyUseCase.updateWorkspace(userId, id, request)
        return ResData.success(result)
    }

    @Operation(summary = "워크스페이스 삭제")
    @DeleteMapping("/workspaces/{id}")
    fun deleteWorkspace(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        agencyUseCase.deleteWorkspace(userId, id)
        return ResData.success(null, "워크스페이스가 삭제되었습니다")
    }

    @Operation(summary = "크리에이터 추가")
    @PostMapping("/workspaces/{workspaceId}/creators")
    fun addCreator(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @RequestBody request: AddCreatorRequest,
    ): ResponseEntity<ResData<AgencyCreatorResponse>> {
        val result = agencyUseCase.addCreator(userId, workspaceId, request)
        return ResData.success(result, "크리에이터가 추가되었습니다")
    }

    @Operation(summary = "크리에이터 제거")
    @DeleteMapping("/workspaces/{workspaceId}/creators/{creatorId}")
    fun removeCreator(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable creatorId: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        agencyUseCase.removeCreator(userId, workspaceId, creatorId)
        return ResData.success(null, "크리에이터가 제거되었습니다")
    }

    @Operation(summary = "클라이언트 포탈 목록 조회")
    @GetMapping("/workspaces/{workspaceId}/portals")
    fun listPortals(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
    ): ResponseEntity<ResData<List<ClientPortalResponse>>> {
        val result = agencyUseCase.listPortals(userId, workspaceId)
        return ResData.success(result)
    }

    @Operation(summary = "클라이언트 포탈 생성")
    @PostMapping("/workspaces/{workspaceId}/portals")
    fun createPortal(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @RequestBody request: CreatePortalRequest,
    ): ResponseEntity<ResData<ClientPortalResponse>> {
        val result = agencyUseCase.createPortal(userId, workspaceId, request)
        return ResData.success(result, "클라이언트 포탈이 생성되었습니다")
    }

    @Operation(summary = "클라이언트 포탈 삭제")
    @DeleteMapping("/workspaces/{workspaceId}/portals/{portalId}")
    fun deletePortal(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable workspaceId: Long,
        @PathVariable portalId: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        agencyUseCase.deletePortal(userId, workspaceId, portalId)
        return ResData.success(null, "클라이언트 포탈이 삭제되었습니다")
    }
}
