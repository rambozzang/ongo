package com.ongo.api.calendarinsights

import com.ongo.application.calendarinsights.CalendarInsightsUseCase
import com.ongo.application.calendarinsights.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "캘린더 인사이트", description = "콘텐츠 업로드 패턴 분석 및 최적 시간 추천")
@RestController
@RequestMapping("/api/v1/calendar-insights")
class CalendarInsightsController(
    private val useCase: CalendarInsightsUseCase
) {

    @Operation(summary = "월별 인사이트 조회")
    @GetMapping
    fun getInsights(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam year: Int,
        @RequestParam month: Int,
    ): ResponseEntity<ResData<List<CalendarInsightResponse>>> {
        val result = useCase.getInsights(userId, year, month)
        return ResData.success(result)
    }

    @Operation(summary = "최적 업로드 시간 조회")
    @GetMapping("/optimal-times")
    fun getOptimalTimeSlots(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) platform: String?,
    ): ResponseEntity<ResData<List<OptimalTimeSlotResponse>>> {
        val result = useCase.getOptimalTimeSlots(userId, platform)
        return ResData.success(result)
    }

    @Operation(summary = "업로드 패턴 조회")
    @GetMapping("/patterns")
    fun getUploadPatterns(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<UploadPatternResponse>>> {
        val result = useCase.getUploadPatterns(userId)
        return ResData.success(result)
    }

    @Operation(summary = "캘린더 인사이트 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<CalendarInsightsSummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
