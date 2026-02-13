package com.ongo.application.goal

import com.ongo.application.goal.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.goal.Goal
import com.ongo.domain.goal.GoalMilestone
import com.ongo.domain.goal.GoalRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GoalUseCase(
    private val goalRepository: GoalRepository,
) {

    fun listGoals(userId: Long): List<GoalResponse> {
        val goals = goalRepository.findByUserId(userId)
        return goals.map { goal ->
            val milestones = goalRepository.findMilestonesByGoalId(goal.id!!)
            goal.toResponse(milestones)
        }
    }

    @Transactional
    fun createGoal(userId: Long, request: CreateGoalRequest): GoalResponse {
        val goal = Goal(
            userId = userId,
            title = request.title,
            description = request.description,
            metricType = request.metricType,
            targetValue = request.targetValue,
            startDate = request.startDate,
            endDate = request.endDate,
        )
        val saved = goalRepository.save(goal)
        return saved.toResponse(emptyList())
    }

    @Transactional
    fun updateGoal(userId: Long, goalId: Long, request: UpdateGoalRequest): GoalResponse {
        val goal = goalRepository.findById(goalId) ?: throw NotFoundException("목표", goalId)
        if (goal.userId != userId) throw ForbiddenException("해당 목표에 대한 권한이 없습니다")

        val updated = goal.copy(
            title = request.title ?: goal.title,
            description = request.description ?: goal.description,
            metricType = request.metricType ?: goal.metricType,
            targetValue = request.targetValue ?: goal.targetValue,
            startDate = request.startDate ?: goal.startDate,
            endDate = request.endDate ?: goal.endDate,
            status = request.status ?: goal.status,
        )
        val saved = goalRepository.update(updated)
        val milestones = goalRepository.findMilestonesByGoalId(goalId)
        return saved.toResponse(milestones)
    }

    @Transactional
    fun deleteGoal(userId: Long, goalId: Long) {
        val goal = goalRepository.findById(goalId) ?: throw NotFoundException("목표", goalId)
        if (goal.userId != userId) throw ForbiddenException("해당 목표에 대한 권한이 없습니다")
        goalRepository.delete(goalId)
    }

    @Transactional
    fun addMilestone(userId: Long, goalId: Long, request: CreateMilestoneRequest): MilestoneResponse {
        val goal = goalRepository.findById(goalId) ?: throw NotFoundException("목표", goalId)
        if (goal.userId != userId) throw ForbiddenException("해당 목표에 대한 권한이 없습니다")

        val milestone = GoalMilestone(
            goalId = goalId,
            title = request.title,
            targetValue = request.targetValue,
        )
        return goalRepository.saveMilestone(milestone).toResponse()
    }

    @Transactional
    fun updateProgress(userId: Long, goalId: Long, request: UpdateProgressRequest): GoalResponse {
        val goal = goalRepository.findById(goalId) ?: throw NotFoundException("목표", goalId)
        if (goal.userId != userId) throw ForbiddenException("해당 목표에 대한 권한이 없습니다")

        val updated = goal.copy(currentValue = request.currentValue)
        val saved = goalRepository.update(updated)

        // Auto-mark milestones as reached
        val milestones = goalRepository.findMilestonesByGoalId(goalId)
        milestones.filter { !it.isReached && request.currentValue >= it.targetValue }.forEach { ms ->
            goalRepository.updateMilestone(ms.copy(isReached = true, reachedAt = java.time.LocalDateTime.now()))
        }

        val updatedMilestones = goalRepository.findMilestonesByGoalId(goalId)
        return saved.toResponse(updatedMilestones)
    }

    private fun Goal.toResponse(milestones: List<GoalMilestone>): GoalResponse = GoalResponse(
        id = id!!,
        title = title,
        description = description,
        metricType = metricType,
        targetValue = targetValue,
        currentValue = currentValue,
        startDate = startDate,
        endDate = endDate,
        status = status,
        milestones = milestones.map { it.toResponse() },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun GoalMilestone.toResponse(): MilestoneResponse = MilestoneResponse(
        id = id!!,
        title = title,
        targetValue = targetValue,
        isReached = isReached,
        reachedAt = reachedAt,
        createdAt = createdAt,
    )
}
