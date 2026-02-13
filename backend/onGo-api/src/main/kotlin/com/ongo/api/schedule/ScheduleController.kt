package com.ongo.api.schedule

import com.ongo.api.config.CurrentUser
import com.ongo.application.schedule.dto.*
import com.ongo.application.schedule.ScheduleUseCase
import com.ongo.common.ResData
import com.ongo.common.annotation.RequiresPermission
import com.ongo.common.enums.Permission
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@Tag(name = "예약 관리", description = "영상 예약 게시 등록, 수정, 취소, 조회")
@RestController
@RequestMapping("/api/v1/schedules")
class ScheduleController(
    private val scheduleUseCase: ScheduleUseCase
) {

    @Operation(
        summary = "예약 등록",
        description = "영상의 예약 게시를 등록합니다. 지정된 시간에 선택한 플랫폼들에 자동으로 게시됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "예약 등록 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (과거 시간 지정 또는 필수 항목 누락)"),
        ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않은 Access Token)"),
        ApiResponse(responseCode = "404", description = "영상을 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @RequiresPermission(Permission.SCHEDULE_CREATE)
    @PostMapping
    fun createSchedule(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateScheduleRequest
    ): ResponseEntity<ResData<ScheduleResponse>> {
        val result = scheduleUseCase.createSchedule(userId, request)
        return ResData.success(result, "예약이 등록되었습니다")
    }

    @Operation(
        summary = "예약 목록 조회",
        description = "사용자의 예약 목록을 조회합니다. 기간(from/to) 및 상태로 필터링할 수 있으며, 캘린더 형태로 반환됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "예약 목록 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않은 Access Token)"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @RequiresPermission(Permission.SCHEDULE_READ)
    @GetMapping
    fun listSchedules(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "조회 시작 일시 (ISO 8601 형식, 예: 2025-01-01T00:00:00)") @RequestParam(required = false) from: LocalDateTime?,
        @Parameter(description = "조회 종료 일시 (ISO 8601 형식, 예: 2025-01-31T23:59:59)") @RequestParam(required = false) to: LocalDateTime?,
        @Parameter(description = "예약 상태 필터 (PENDING, PUBLISHED, CANCELLED 등)") @RequestParam(required = false) status: String?
    ): ResponseEntity<ResData<ScheduleCalendarResponse>> {
        val result = scheduleUseCase.getSchedules(userId, from, to)
        return ResData.success(result)
    }

    @Operation(
        summary = "예약 상세 조회",
        description = "지정된 예약의 상세 정보를 조회합니다. 예약 시간, 대상 플랫폼, 영상 정보 등이 포함됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "예약 상세 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않은 Access Token)"),
        ApiResponse(responseCode = "404", description = "예약을 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @GetMapping("/{id}")
    fun getSchedule(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "조회할 예약 ID") @PathVariable id: Long
    ): ResponseEntity<ResData<ScheduleResponse>> {
        val result = scheduleUseCase.getSchedule(userId, id)
        return ResData.success(result)
    }

    @Operation(
        summary = "예약 수정",
        description = "지정된 예약의 게시 시간, 대상 플랫폼 등을 수정합니다. PENDING 상태인 예약만 수정할 수 있습니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "예약 수정 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (과거 시간 지정 또는 수정 불가능한 상태)"),
        ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않은 Access Token)"),
        ApiResponse(responseCode = "404", description = "예약을 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @RequiresPermission(Permission.SCHEDULE_UPDATE)
    @PutMapping("/{id}")
    fun updateSchedule(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "수정할 예약 ID") @PathVariable id: Long,
        @RequestBody request: UpdateScheduleRequest
    ): ResponseEntity<ResData<ScheduleResponse>> {
        val result = scheduleUseCase.updateSchedule(userId, id, request)
        return ResData.success(result, "예약이 수정되었습니다")
    }

    @Operation(
        summary = "예약 취소",
        description = "지정된 예약을 취소합니다. PENDING 상태인 예약만 취소할 수 있습니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "예약 취소 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않은 Access Token)"),
        ApiResponse(responseCode = "404", description = "예약을 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @RequiresPermission(Permission.SCHEDULE_DELETE)
    @DeleteMapping("/{id}")
    fun cancelSchedule(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "취소할 예약 ID") @PathVariable id: Long
    ): ResponseEntity<ResData<Nothing?>> {
        scheduleUseCase.cancelSchedule(userId, id)
        return ResData.success(null, "예약이 취소되었습니다")
    }
}
