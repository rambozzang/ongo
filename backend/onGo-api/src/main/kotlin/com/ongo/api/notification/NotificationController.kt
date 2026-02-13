package com.ongo.api.notification

import com.ongo.api.config.CurrentUser
import com.ongo.application.notification.NotificationUseCase
import com.ongo.application.notification.dto.NotificationListResponse
import com.ongo.application.notification.dto.UnreadCountResponse
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "알림", description = "사용자 알림 관리")
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationUseCase: NotificationUseCase,
) {

    @Operation(summary = "알림 목록 조회", description = "사용자의 알림 목록을 페이지네이션으로 조회합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "알림 목록 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
    )
    @GetMapping
    fun listNotifications(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ResData<NotificationListResponse>> {
        val result = notificationUseCase.listNotifications(userId, page, size)
        return ResData.success(result)
    }

    @Operation(summary = "알림 읽음 처리", description = "특정 알림을 읽음으로 표시합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "읽음 처리 성공"),
    )
    @PutMapping("/{id}/read")
    fun markAsRead(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "알림 ID") @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        notificationUseCase.markAsRead(userId, id)
        return ResData.success(null, "읽음 처리되었습니다")
    }

    @Operation(summary = "모든 알림 읽음 처리", description = "모든 알림을 읽음으로 표시합니다.")
    @PutMapping("/read-all")
    fun markAllAsRead(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        notificationUseCase.markAllAsRead(userId)
        return ResData.success(null, "모두 읽음 처리되었습니다")
    }

    @Operation(summary = "읽지 않은 알림 수 조회", description = "읽지 않은 알림의 총 수를 반환합니다.")
    @GetMapping("/unread-count")
    fun getUnreadCount(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<UnreadCountResponse>> {
        val result = notificationUseCase.getUnreadCount(userId)
        return ResData.success(result)
    }

    @Operation(summary = "알림 삭제", description = "특정 알림을 삭제합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "삭제 성공"),
    )
    @DeleteMapping("/{id}")
    fun deleteNotification(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "알림 ID") @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        notificationUseCase.deleteNotification(userId, id)
        return ResData.success(null, "알림이 삭제되었습니다")
    }
}
