package com.ongo.api.admin

import com.ongo.application.admin.AdminUseCase
import com.ongo.application.admin.dto.*
import com.ongo.common.ResData
import com.ongo.common.config.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@Tag(name = "관리자", description = "관리자 전용 사용자 관리, 데이터 조회, 역할/상태 변경")
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminController(
    private val adminUseCase: AdminUseCase,
) {

    // ─── 사용자 관리 ─────────────────────────────────

    @Operation(summary = "사용자 목록 조회", description = "검색 및 페이징이 가능한 사용자 목록을 조회합니다.")
    @GetMapping("/users")
    fun getUsers(
        @RequestParam(required = false) query: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ResData<PageResponse<AdminUserListItem>>> {
        val result = adminUseCase.getUsers(query, page, size)
        return ResData.success(result)
    }

    @Operation(summary = "사용자 상세 조회", description = "스토리지 사용량, 구독 정보 등 상세 정보를 조회합니다.")
    @GetMapping("/users/{userId}")
    fun getUserDetail(@PathVariable userId: Long): ResponseEntity<ResData<AdminUserDetail>> {
        val result = adminUseCase.getUserDetail(userId)
        return ResData.success(result)
    }

    @Operation(summary = "스토리지 한도 조정", description = "사용자의 스토리지 한도를 조정합니다. null이면 Plan 기본값으로 리셋됩니다.")
    @PutMapping("/users/{userId}/storage-quota")
    fun updateStorageQuota(
        @PathVariable userId: Long,
        @RequestBody request: StorageQuotaUpdateRequest,
    ): ResponseEntity<ResData<Nothing>> {
        adminUseCase.updateStorageQuota(userId, request.limitBytes)
        return ResponseEntity.ok(ResData(success = true, message = "스토리지 한도가 업데이트되었습니다"))
    }

    // ─── CS 대응: 사용자 데이터 조회 (읽기 전용) ─────────────

    @Operation(summary = "사용자 영상 목록 조회", description = "특정 사용자의 영상 목록을 페이징으로 조회합니다.")
    @GetMapping("/users/{userId}/videos")
    fun getUserVideos(
        @PathVariable userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ResData<PageResponse<AdminVideoItem>>> {
        val result = adminUseCase.getUserVideos(userId, page, size)
        return ResData.success(result)
    }

    @Operation(summary = "사용자 채널 목록 조회", description = "특정 사용자의 플랫폼 연동 채널 목록을 조회합니다.")
    @GetMapping("/users/{userId}/channels")
    fun getUserChannels(
        @PathVariable userId: Long,
    ): ResponseEntity<ResData<List<AdminChannelItem>>> {
        val result = adminUseCase.getUserChannels(userId)
        return ResData.success(result)
    }

    @Operation(summary = "사용자 구독 정보 조회", description = "특정 사용자의 구독 플랜, 결제 주기, 상태 등을 조회합니다.")
    @GetMapping("/users/{userId}/subscription")
    fun getUserSubscription(
        @PathVariable userId: Long,
    ): ResponseEntity<ResData<AdminSubscriptionDetail>> {
        val result = adminUseCase.getUserSubscription(userId)
        return ResData.success(result)
    }

    // ─── 사용자 역할 및 상태 변경 ─────────────────────

    @Operation(summary = "사용자 역할 변경", description = "사용자의 역할을 변경합니다. (USER, ADMIN)")
    @PutMapping("/users/{userId}/role")
    fun updateUserRole(
        @PathVariable userId: Long,
        @RequestBody request: UpdateRoleRequest,
    ): ResponseEntity<ResData<Nothing>> {
        adminUseCase.updateUserRole(userId, request.role)
        return ResponseEntity.ok(ResData(success = true, message = "사용자 역할이 변경되었습니다"))
    }

    @Operation(summary = "사용자 비활성화", description = "사용자의 구독을 정지하여 서비스 이용을 차단합니다.")
    @PostMapping("/users/{userId}/deactivate")
    fun deactivateUser(
        @PathVariable userId: Long,
    ): ResponseEntity<ResData<Nothing>> {
        adminUseCase.deactivateUser(userId)
        return ResponseEntity.ok(ResData(success = true, message = "사용자가 비활성화되었습니다"))
    }

    @Operation(summary = "사용자 활성화", description = "비활성화된 사용자를 다시 활성화합니다.")
    @PostMapping("/users/{userId}/activate")
    fun activateUser(
        @PathVariable userId: Long,
    ): ResponseEntity<ResData<Nothing>> {
        adminUseCase.activateUser(userId)
        return ResponseEntity.ok(ResData(success = true, message = "사용자가 활성화되었습니다"))
    }
}

data class StorageQuotaUpdateRequest(
    val limitBytes: Long?,
)
