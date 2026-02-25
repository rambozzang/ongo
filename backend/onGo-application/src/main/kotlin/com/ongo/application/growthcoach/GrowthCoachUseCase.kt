package com.ongo.application.growthcoach

import com.ongo.application.growthcoach.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.growthcoach.GrowthCoachRepository
import com.ongo.domain.growthcoach.GrowthGoal
import com.ongo.domain.growthcoach.WeeklyReport
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GrowthCoachUseCase(
    private val growthCoachRepository: GrowthCoachRepository,
) {

    fun listGoals(userId: Long): List<GrowthGoalResponse> {
        return growthCoachRepository.findGoalsByUserId(userId).map { it.toResponse() }
    }

    @Transactional
    fun createGoal(userId: Long, request: CreateGrowthGoalRequest): GrowthGoalResponse {
        val goal = GrowthGoal(
            userId = userId,
            type = request.type,
            targetValue = request.targetValue,
            currentValue = request.currentValue,
            deadline = request.deadline,
            progress = request.progress,
        )
        return growthCoachRepository.saveGoal(goal).toResponse()
    }

    @Transactional
    fun updateGoal(userId: Long, goalId: Long, request: UpdateGrowthGoalRequest): GrowthGoalResponse {
        val goal = growthCoachRepository.findGoalById(goalId) ?: throw NotFoundException("성장 목표", goalId)
        if (goal.userId != userId) throw ForbiddenException("해당 성장 목표에 대한 권한이 없습니다")

        val updated = goal.copy(
            type = request.type ?: goal.type,
            targetValue = request.targetValue ?: goal.targetValue,
            currentValue = request.currentValue ?: goal.currentValue,
            deadline = request.deadline ?: goal.deadline,
            progress = request.progress ?: goal.progress,
        )
        return growthCoachRepository.updateGoal(updated).toResponse()
    }

    @Transactional
    fun deleteGoal(userId: Long, goalId: Long) {
        val goal = growthCoachRepository.findGoalById(goalId) ?: throw NotFoundException("성장 목표", goalId)
        if (goal.userId != userId) throw ForbiddenException("해당 성장 목표에 대한 권한이 없습니다")
        growthCoachRepository.deleteGoal(goalId)
    }

    fun listReports(userId: Long): List<WeeklyReportResponse> {
        return growthCoachRepository.findReportsByUserId(userId).map { it.toResponse() }
    }

    @Transactional
    fun generateReport(userId: Long): WeeklyReportResponse {
        val report = WeeklyReport(
            userId = userId,
            weekStart = "",
            weekEnd = "",
            summary = "",
        )
        return growthCoachRepository.saveReport(report).toResponse()
    }

    private fun GrowthGoal.toResponse() = GrowthGoalResponse(
        id = id!!,
        type = type,
        targetValue = targetValue,
        currentValue = currentValue,
        deadline = deadline,
        progress = progress,
        createdAt = createdAt,
    )

    private fun WeeklyReport.toResponse() = WeeklyReportResponse(
        id = id!!,
        weekStart = weekStart,
        weekEnd = weekEnd,
        summary = summary,
        highlights = highlights,
        concerns = concerns,
        subscriberGrowth = subscriberGrowth,
        viewsGrowth = viewsGrowth,
        engagementChange = engagementChange,
        topContent = topContent,
        actionItems = actionItems,
        overallScore = overallScore,
        generatedAt = generatedAt,
    )
}
