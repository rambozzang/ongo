package com.ongo.api.team

import com.ongo.api.config.CurrentUser
import com.ongo.application.team.TeamUseCase
import com.ongo.application.team.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.RequiresPermission
import com.ongo.common.enums.Permission
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "팀 관리", description = "팀 멤버 초대, 역할 변경, 제거, 권한 관리")
@RestController
@RequestMapping("/api/v1/team")
class TeamController(
    private val teamUseCase: TeamUseCase,
) {

    @Operation(summary = "팀 멤버 목록 조회", description = "현재 사용자의 팀 멤버 목록을 조회합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "팀 멤버 목록 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
    )
    @GetMapping("/members")
    fun listMembers(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<TeamMemberResponse>>> {
        val result = teamUseCase.listMembers(userId)
        return ResData.success(result)
    }

    @Operation(summary = "팀 멤버 초대", description = "이메일로 팀 멤버를 초대합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "초대 성공"),
        ApiResponse(responseCode = "400", description = "이미 초대된 이메일"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
    )
    @RequiresPermission(Permission.TEAM_INVITE)
    @PostMapping("/invite")
    fun inviteMember(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Valid @RequestBody request: InviteMemberRequest,
    ): ResponseEntity<ResData<TeamMemberResponse>> {
        val result = teamUseCase.inviteMember(userId, request)
        return ResData.success(result, "멤버가 초대되었습니다")
    }

    @Operation(summary = "멤버 역할 변경", description = "팀 멤버의 역할을 변경합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "역할 변경 성공"),
        ApiResponse(responseCode = "404", description = "멤버를 찾을 수 없음"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
    )
    @RequiresPermission(Permission.TEAM_MANAGE)
    @PutMapping("/members/{id}/role")
    fun updateRole(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "멤버 ID") @PathVariable id: Long,
        @Valid @RequestBody request: UpdateRoleRequest,
    ): ResponseEntity<ResData<TeamMemberResponse>> {
        val result = teamUseCase.updateRole(userId, id, request)
        return ResData.success(result, "역할이 변경되었습니다")
    }

    @Operation(summary = "멤버 제거", description = "팀에서 멤버를 제거합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "멤버 제거 성공"),
        ApiResponse(responseCode = "404", description = "멤버를 찾을 수 없음"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
    )
    @RequiresPermission(Permission.TEAM_REMOVE)
    @DeleteMapping("/members/{id}")
    fun removeMember(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "멤버 ID") @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        teamUseCase.removeMember(userId, id)
        return ResData.success(null, "멤버가 제거되었습니다")
    }

    @Operation(summary = "팀 권한 전체 조회", description = "팀의 모든 멤버 권한을 조회합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "권한 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
    )
    @GetMapping("/permissions")
    fun getTeamPermissions(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<TeamPermissionsResponse>> {
        val result = teamUseCase.getTeamPermissions(userId)
        return ResData.success(result)
    }

    @Operation(summary = "멤버 권한 조회", description = "특정 멤버의 유효 권한을 조회합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "멤버 권한 조회 성공"),
        ApiResponse(responseCode = "404", description = "멤버를 찾을 수 없음"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
    )
    @GetMapping("/members/{memberId}/permissions")
    fun getMemberPermissions(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "멤버 ID") @PathVariable memberId: Long,
    ): ResponseEntity<ResData<MemberPermissionsResponse>> {
        val result = teamUseCase.getMemberPermissions(userId, memberId)
        return ResData.success(result)
    }

    @Operation(summary = "멤버 권한 수정", description = "특정 멤버의 권한 오버라이드를 수정합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "권한 수정 성공"),
        ApiResponse(responseCode = "404", description = "멤버를 찾을 수 없음"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "403", description = "권한 없음"),
    )
    @RequiresPermission(Permission.TEAM_MANAGE)
    @PutMapping("/members/{memberId}/permissions")
    fun updateMemberPermissions(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "멤버 ID") @PathVariable memberId: Long,
        @Valid @RequestBody request: UpdatePermissionsRequest,
    ): ResponseEntity<ResData<MemberPermissionsResponse>> {
        val result = teamUseCase.updateMemberPermissions(userId, memberId, request)
        return ResData.success(result, "권한이 수정되었습니다")
    }

    @Operation(summary = "권한 카탈로그 조회", description = "전체 권한 목록 및 역할별 기본 권한을 조회합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "권한 카탈로그 조회 성공"),
    )
    @GetMapping("/permissions/catalog")
    fun getPermissionCatalog(): ResponseEntity<ResData<PermissionCatalogResponse>> {
        val result = teamUseCase.getPermissionCatalog()
        return ResData.success(result)
    }
}
