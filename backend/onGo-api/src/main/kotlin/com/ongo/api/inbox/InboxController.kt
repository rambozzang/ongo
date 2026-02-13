package com.ongo.api.inbox

import com.ongo.api.config.CurrentUser
import com.ongo.application.inbox.InboxUseCase
import com.ongo.application.inbox.dto.InboxListResponse
import com.ongo.application.inbox.dto.InboxMessageResponse
import com.ongo.application.inbox.dto.UnreadCountResponse
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "받은 메시지함", description = "플랫폼 댓글, DM 등 수신 메시지 관리")
@RestController
@RequestMapping("/api/v1/inbox")
class InboxController(
    private val inboxUseCase: InboxUseCase,
) {

    @Operation(summary = "메시지 목록 조회", description = "필터와 페이지네이션을 적용하여 받은 메시지를 조회합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "메시지 목록 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
    )
    @GetMapping
    fun listMessages(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) platform: String?,
        @RequestParam(required = false) isRead: Boolean?,
        @RequestParam(required = false) type: String?,
    ): ResponseEntity<ResData<InboxListResponse>> {
        val result = inboxUseCase.listMessages(userId, page, size, platform, isRead, type)
        return ResData.success(result)
    }

    @Operation(summary = "메시지 읽음 처리", description = "특정 메시지를 읽음으로 표시합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "읽음 처리 성공"),
        ApiResponse(responseCode = "404", description = "메시지를 찾을 수 없음"),
    )
    @PutMapping("/{id}/read")
    fun markAsRead(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "메시지 ID") @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        inboxUseCase.markAsRead(userId, id)
        return ResData.success(null, "읽음 처리되었습니다")
    }

    @Operation(summary = "모든 메시지 읽음 처리", description = "모든 수신 메시지를 읽음으로 표시합니다.")
    @PutMapping("/read-all")
    fun markAllAsRead(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        inboxUseCase.markAllAsRead(userId)
        return ResData.success(null, "모두 읽음 처리되었습니다")
    }

    @Operation(summary = "메시지 즐겨찾기 토글", description = "특정 메시지의 즐겨찾기 상태를 토글합니다.")
    @PutMapping("/{id}/star")
    fun toggleStar(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "메시지 ID") @PathVariable id: Long,
    ): ResponseEntity<ResData<InboxMessageResponse>> {
        val result = inboxUseCase.toggleStar(userId, id)
        return ResData.success(result)
    }

    @Operation(summary = "읽지 않은 메시지 수 조회", description = "읽지 않은 메시지의 총 수를 반환합니다.")
    @GetMapping("/unread-count")
    fun getUnreadCount(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<UnreadCountResponse>> {
        val result = inboxUseCase.getUnreadCount(userId)
        return ResData.success(result)
    }
}
