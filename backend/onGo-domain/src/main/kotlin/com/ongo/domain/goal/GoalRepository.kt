package com.ongo.domain.goal

interface GoalRepository {
    fun findById(id: Long): Goal?
    fun findByUserId(userId: Long): List<Goal>
    fun save(goal: Goal): Goal
    fun update(goal: Goal): Goal
    fun delete(id: Long)
    fun findMilestonesByGoalId(goalId: Long): List<GoalMilestone>
    fun saveMilestone(milestone: GoalMilestone): GoalMilestone
    fun deleteMilestone(id: Long)
    fun updateMilestone(milestone: GoalMilestone): GoalMilestone
}
