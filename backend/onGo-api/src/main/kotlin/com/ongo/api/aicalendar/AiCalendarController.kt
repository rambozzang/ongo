package com.ongo.api.aicalendar

import com.ongo.application.aicalendar.AiCalendarUseCase
import com.ongo.application.aicalendar.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "AI 캘린더", description = "AI 기반 콘텐츠 캘린더 자동 생성")
@RestController
@RequestMapping("/api/v1/ai-calendars")
class AiCalendarController(
    private val aiCalendarUseCase: AiCalendarUseCase
) {

    @Operation(summary = "캘린더 목록 조회")
    @GetMapping
    fun listCalendars(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<AiCalendarResponse>>> {
        val result = aiCalendarUseCase.listCalendars(userId)
        return ResData.success(result)
    }

    @Operation(summary = "캘린더 상세 조회")
    @GetMapping("/{id}")
    fun getCalendar(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<AiCalendarResponse>> {
        val result = aiCalendarUseCase.getCalendar(userId, id)
        return ResData.success(result)
    }

    @Operation(summary = "AI 캘린더 생성")
    @PostMapping
    fun generateCalendar(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: GenerateCalendarRequest,
    ): ResponseEntity<ResData<AiCalendarResponse>> {
        val result = aiCalendarUseCase.generateCalendar(userId, request)
        return ResData.success(result, "캘린더가 생성되었습니다")
    }

    @Operation(summary = "캘린더 수정")
    @PutMapping("/{id}")
    fun updateCalendar(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateCalendarRequest,
    ): ResponseEntity<ResData<AiCalendarResponse>> {
        val result = aiCalendarUseCase.updateCalendar(userId, id, request)
        return ResData.success(result)
    }

    @Operation(summary = "캘린더 삭제")
    @DeleteMapping("/{id}")
    fun deleteCalendar(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        aiCalendarUseCase.deleteCalendar(userId, id)
        return ResData.success(null, "캘린더가 삭제되었습니다")
    }
}
