package com.ongo.api.goal

import com.ongo.application.goal.GoalUseCase
import com.ongo.application.goal.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "목표 관리", description = "크리에이터 목표 설정 및 추적")
@RestController
@RequestMapping("/api/v1/goals")
class GoalController(
    private val goalUseCase: GoalUseCase
) {

    @Operation(summary = "목표 목록 조회")
    @GetMapping
    fun listGoals(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<GoalResponse>>> {
        val result = goalUseCase.listGoals(userId)
        return ResData.success(result)
    }

    @Operation(summary = "목표 생성")
    @PostMapping
    fun createGoal(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateGoalRequest,
    ): ResponseEntity<ResData<GoalResponse>> {
        val result = goalUseCase.createGoal(userId, request)
        return ResData.success(result, "목표가 생성되었습니다")
    }

    @Operation(summary = "목표 수정")
    @PutMapping("/{id}")
    fun updateGoal(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateGoalRequest,
    ): ResponseEntity<ResData<GoalResponse>> {
        val result = goalUseCase.updateGoal(userId, id, request)
        return ResData.success(result)
    }

    @Operation(summary = "목표 삭제")
    @DeleteMapping("/{id}")
    fun deleteGoal(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        goalUseCase.deleteGoal(userId, id)
        return ResData.success(null, "목표가 삭제되었습니다")
    }

    @Operation(summary = "마일스톤 추가")
    @PostMapping("/{id}/milestones")
    fun addMilestone(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: CreateMilestoneRequest,
    ): ResponseEntity<ResData<MilestoneResponse>> {
        val result = goalUseCase.addMilestone(userId, id, request)
        return ResData.success(result, "마일스톤이 추가되었습니다")
    }

    @Operation(summary = "목표 진행률 업데이트")
    @PutMapping("/{id}/progress")
    fun updateProgress(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateProgressRequest,
    ): ResponseEntity<ResData<GoalResponse>> {
        val result = goalUseCase.updateProgress(userId, id, request)
        return ResData.success(result)
    }
}
