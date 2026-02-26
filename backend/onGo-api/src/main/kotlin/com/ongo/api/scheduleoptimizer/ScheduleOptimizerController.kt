package com.ongo.api.scheduleoptimizer

import com.ongo.application.scheduleoptimizer.ScheduleOptimizerUseCase
import com.ongo.application.scheduleoptimizer.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "AI 일정 최적화", description = "AI 기반 최적 게시 시간 추천")
@RestController
@RequestMapping("/api/v1/schedule-optimizer")
class ScheduleOptimizerController(
    private val scheduleOptimizerUseCase: ScheduleOptimizerUseCase
) {

    @Operation(summary = "최적 시간대 조회")
    @GetMapping("/slots")
    fun getSlots(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam platform: String,
    ): ResponseEntity<ResData<List<OptimalSlotResponse>>> {
        val result = scheduleOptimizerUseCase.getSlots(userId, platform)
        return ResData.success(result)
    }

    @Operation(summary = "일정 추천 목록")
    @GetMapping("/recommendations")
    fun getRecommendations(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<ScheduleRecommendationResponse>>> {
        val result = scheduleOptimizerUseCase.getRecommendations(userId)
        return ResData.success(result)
    }

    @Operation(summary = "추천 적용")
    @PostMapping("/recommendations/{id}/apply")
    fun applyRecommendation(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<ScheduleRecommendationResponse>> {
        val result = scheduleOptimizerUseCase.applyRecommendation(userId, id)
        return ResData.success(result, "추천 일정이 적용되었습니다")
    }

    @Operation(summary = "일정 최적화 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<ScheduleOptimizerSummaryResponse>> {
        val result = scheduleOptimizerUseCase.getSummary(userId)
        return ResData.success(result)
    }
}
