package com.ongo.api.revenuegoal

import com.ongo.application.revenuegoal.RevenueGoalUseCase
import com.ongo.application.revenuegoal.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "수익 목표", description = "수익 목표 설정 및 추적")
@RestController
@RequestMapping("/api/v1/revenue-goals")
class RevenueGoalController(
    private val useCase: RevenueGoalUseCase
) {

    @Operation(summary = "수익 목표 목록 조회")
    @GetMapping
    fun getGoals(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) status: String?,
    ): ResponseEntity<ResData<List<RevenueGoalResponse>>> {
        val result = useCase.getGoals(userId, status)
        return ResData.success(result)
    }

    @Operation(summary = "수익 목표 생성")
    @PostMapping
    fun createGoal(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateRevenueGoalRequest,
    ): ResponseEntity<ResData<RevenueGoalResponse>> {
        val result = useCase.createGoal(userId, request)
        return ResData.success(result)
    }

    @Operation(summary = "목표 마일스톤 목록 조회")
    @GetMapping("/{goalId}/milestones")
    fun getMilestones(
        @PathVariable goalId: Long,
    ): ResponseEntity<ResData<List<RevenueGoalMilestoneResponse>>> {
        val result = useCase.getMilestones(goalId)
        return ResData.success(result)
    }

    @Operation(summary = "수익 목표 삭제")
    @DeleteMapping("/{id}")
    fun deleteGoal(
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Unit>> {
        useCase.deleteGoal(id)
        return ResData.success(Unit)
    }

    @Operation(summary = "수익 목표 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<RevenueGoalSummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
