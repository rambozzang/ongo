package com.ongo.domain.revenuegoal

interface RevenueGoalMilestoneRepository {
    fun findByGoalId(goalId: Long): List<RevenueGoalMilestone>
}
