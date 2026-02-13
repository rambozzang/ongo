package com.ongo.api.recurring

import com.ongo.api.config.CurrentUser
import com.ongo.application.recurring.RecurringScheduleUseCase
import com.ongo.application.recurring.dto.*
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "반복 예약", description = "반복 예약 CRUD 및 토글")
@RestController
@RequestMapping("/api/v1/schedules/recurring")
class RecurringScheduleController(
    private val recurringScheduleUseCase: RecurringScheduleUseCase,
) {

    @Operation(summary = "반복 예약 목록 조회")
    @GetMapping
    fun listSchedules(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<RecurringScheduleResponse>>> {
        val result = recurringScheduleUseCase.listSchedules(userId)
        return ResData.success(result)
    }

    @Operation(summary = "반복 예약 생성")
    @PostMapping
    fun createSchedule(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateRecurringScheduleRequest,
    ): ResponseEntity<ResData<RecurringScheduleResponse>> {
        val result = recurringScheduleUseCase.createSchedule(userId, request)
        return ResData.success(result, "반복 예약이 생성되었습니다")
    }

    @Operation(summary = "반복 예약 수정")
    @PutMapping("/{id}")
    fun updateSchedule(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateRecurringScheduleRequest,
    ): ResponseEntity<ResData<RecurringScheduleResponse>> {
        val result = recurringScheduleUseCase.updateSchedule(userId, id, request)
        return ResData.success(result, "반복 예약이 수정되었습니다")
    }

    @Operation(summary = "반복 예약 삭제")
    @DeleteMapping("/{id}")
    fun deleteSchedule(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        recurringScheduleUseCase.deleteSchedule(userId, id)
        return ResData.success(null, "반복 예약이 삭제되었습니다")
    }

    @Operation(summary = "반복 예약 활성/비활성 토글")
    @PutMapping("/{id}/toggle")
    fun toggleSchedule(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<RecurringScheduleResponse>> {
        val result = recurringScheduleUseCase.toggleSchedule(userId, id)
        return ResData.success(result)
    }
}
