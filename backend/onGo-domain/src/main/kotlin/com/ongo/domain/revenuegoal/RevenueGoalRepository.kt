package com.ongo.domain.revenuegoal

interface RevenueGoalRepository {
    fun findById(id: Long): RevenueGoal?
    fun findByWorkspaceId(workspaceId: Long): List<RevenueGoal>
    fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<RevenueGoal>
    fun save(goal: RevenueGoal): RevenueGoal
    fun deleteById(id: Long)
}
