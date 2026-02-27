package com.ongo.application.revenuegoal

import com.ongo.application.revenuegoal.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.revenuegoal.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class RevenueGoalUseCase(
    private val goalRepository: RevenueGoalRepository,
    private val milestoneRepository: RevenueGoalMilestoneRepository,
) {

    fun getGoal(workspaceId: Long, goalId: Long): RevenueGoalResponse {
        val goal = goalRepository.findById(goalId)
            ?: throw NotFoundException("수익 목표", goalId)
        return toGoalResponse(goal)
    }

    fun getGoals(workspaceId: Long, status: String?): List<RevenueGoalResponse> {
        val goals = if (status != null) {
            goalRepository.findByWorkspaceIdAndStatus(workspaceId, status)
        } else {
            goalRepository.findByWorkspaceId(workspaceId)
        }
        return goals.map { toGoalResponse(it) }
    }

    @Transactional
    fun createGoal(workspaceId: Long, request: CreateRevenueGoalRequest): RevenueGoalResponse {
        val goal = RevenueGoal(
            workspaceId = workspaceId,
            name = request.name,
            targetAmount = request.targetAmount,
            period = request.period,
            platform = request.platform,
            startDate = LocalDate.now(),
            endDate = LocalDate.now().plusMonths(1),
        )
        return toGoalResponse(goalRepository.save(goal))
    }

    fun getMilestones(goalId: Long): List<RevenueGoalMilestoneResponse> {
        return milestoneRepository.findByGoalId(goalId).map { toMilestoneResponse(it) }
    }

    @Transactional
    fun deleteGoal(workspaceId: Long, id: Long) {
        val goal = goalRepository.findById(id)
            ?: throw NotFoundException("수익 목표", id)
        if (goal.workspaceId != workspaceId) {
            throw ForbiddenException("해당 수익 목표에 대한 권한이 없습니다")
        }
        goalRepository.deleteById(id)
    }

    fun getSummary(workspaceId: Long): RevenueGoalSummaryResponse {
        val goals = goalRepository.findByWorkspaceId(workspaceId)
        val active = goals.count { it.status == "ACTIVE" }
        val avgProgress = if (goals.isNotEmpty()) goals.map { it.progress }.average() else 0.0
        val totalTarget = goals.sumOf { it.targetAmount }
        val totalCurrent = goals.sumOf { it.currentAmount }
        return RevenueGoalSummaryResponse(goals.size, active, avgProgress, totalTarget, totalCurrent)
    }

    private fun toGoalResponse(g: RevenueGoal) = RevenueGoalResponse(
        id = g.id, name = g.name, targetAmount = g.targetAmount,
        currentAmount = g.currentAmount, currency = g.currency,
        period = g.period, platform = g.platform, status = g.status,
        progress = g.progress, startDate = g.startDate.toString(), endDate = g.endDate.toString(),
    )

    private fun toMilestoneResponse(m: RevenueGoalMilestone) = RevenueGoalMilestoneResponse(
        id = m.id, goalId = m.goalId, label = m.label,
        targetAmount = m.targetAmount, reached = m.reached,
        reachedAt = m.reachedAt?.toString(),
    )
}
