package com.ongo.api.growthcoach

import com.ongo.application.growthcoach.GrowthCoachUseCase
import com.ongo.application.growthcoach.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "성장 코치", description = "성장 목표 관리 및 주간 리포트")
@RestController
@RequestMapping("/api/v1/growth-coach")
class GrowthCoachController(
    private val growthCoachUseCase: GrowthCoachUseCase
) {

    @Operation(summary = "성장 목표 목록 조회")
    @GetMapping("/goals")
    fun listGoals(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<GrowthGoalResponse>>> {
        val result = growthCoachUseCase.listGoals(userId)
        return ResData.success(result)
    }

    @Operation(summary = "성장 목표 생성")
    @PostMapping("/goals")
    fun createGoal(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateGrowthGoalRequest,
    ): ResponseEntity<ResData<GrowthGoalResponse>> {
        val result = growthCoachUseCase.createGoal(userId, request)
        return ResData.success(result, "성장 목표가 생성되었습니다")
    }

    @Operation(summary = "성장 목표 수정")
    @PutMapping("/goals/{id}")
    fun updateGoal(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateGrowthGoalRequest,
    ): ResponseEntity<ResData<GrowthGoalResponse>> {
        val result = growthCoachUseCase.updateGoal(userId, id, request)
        return ResData.success(result)
    }

    @Operation(summary = "성장 목표 삭제")
    @DeleteMapping("/goals/{id}")
    fun deleteGoal(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        growthCoachUseCase.deleteGoal(userId, id)
        return ResData.success(null, "성장 목표가 삭제되었습니다")
    }

    @Operation(summary = "주간 리포트 목록 조회")
    @GetMapping("/reports")
    fun listReports(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<WeeklyReportResponse>>> {
        val result = growthCoachUseCase.listReports(userId)
        return ResData.success(result)
    }

    @Operation(summary = "주간 리포트 생성")
    @PostMapping("/reports/generate")
    fun generateReport(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<WeeklyReportResponse>> {
        val result = growthCoachUseCase.generateReport(userId)
        return ResData.success(result, "주간 리포트가 생성되었습니다")
    }
}
