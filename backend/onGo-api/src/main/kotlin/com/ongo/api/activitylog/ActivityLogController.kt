package com.ongo.api.activitylog

import com.ongo.api.config.CurrentUser
import com.ongo.application.activitylog.ActivityLogUseCase
import com.ongo.application.activitylog.dto.ActivityLogListResponse
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@Tag(name = "활동 로그", description = "사용자 활동 내역 조회")
@RestController
@RequestMapping("/api/v1/activity-logs")
class ActivityLogController(
    private val activityLogUseCase: ActivityLogUseCase,
) {

    @Operation(summary = "활동 로그 조회", description = "필터와 페이지네이션을 적용하여 활동 로그를 조회합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "활동 로그 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
    )
    @GetMapping
    fun listLogs(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) action: String?,
        @RequestParam(required = false) entityType: String?,
        @RequestParam(required = false) startDate: LocalDateTime?,
        @RequestParam(required = false) endDate: LocalDateTime?,
    ): ResponseEntity<ResData<ActivityLogListResponse>> {
        val result = activityLogUseCase.listLogs(userId, page, size, action, entityType, startDate, endDate)
        return ResData.success(result)
    }
}
