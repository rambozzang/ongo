package com.ongo.api.contentcalendarai

import com.ongo.application.contentcalendarai.ContentCalendarAiUseCase
import com.ongo.application.contentcalendarai.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "AI 콘텐츠 캘린더", description = "AI 기반 최적 콘텐츠 일정 생성 및 추천")
@RestController
@RequestMapping("/api/v1/content-calendar-ai")
class ContentCalendarAiController(
    private val useCase: ContentCalendarAiUseCase
) {

    @Operation(summary = "제안 목록 조회")
    @GetMapping
    fun getSuggestions(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) status: String?,
    ): ResponseEntity<ResData<List<CalendarSuggestionResponse>>> {
        val result = useCase.getSuggestions(userId, status)
        return ResData.success(result)
    }

    @Operation(summary = "AI 캘린더 생성")
    @PostMapping("/generate")
    fun generate(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: GenerateCalendarRequest,
    ): ResponseEntity<ResData<List<CalendarSuggestionResponse>>> {
        val result = useCase.generate(userId, request)
        return ResData.success(result)
    }

    @Operation(summary = "최적 시간 슬롯 조회")
    @GetMapping("/slots")
    fun getSlots(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam startDate: String,
        @RequestParam endDate: String,
    ): ResponseEntity<ResData<List<CalendarAiSlotResponse>>> {
        val result = useCase.getSlots(userId, startDate, endDate)
        return ResData.success(result)
    }

    @Operation(summary = "제안 수락")
    @PostMapping("/{id}/accept")
    fun acceptSuggestion(
        @PathVariable id: Long,
    ): ResponseEntity<ResData<CalendarSuggestionResponse?>> {
        val result = useCase.acceptSuggestion(id)
        return ResData.success(result)
    }

    @Operation(summary = "AI 캘린더 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<ContentCalendarAiSummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
