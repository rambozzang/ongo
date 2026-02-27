package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.revenuegoal.*
import org.springframework.stereotype.Repository

@Repository
class RevenueGoalStubRepository : RevenueGoalRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<RevenueGoal> = emptyList()
    override fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<RevenueGoal> = emptyList()
    override fun save(goal: RevenueGoal): RevenueGoal = goal.copy(id = 1)
    override fun deleteById(id: Long) {}
}

@Repository
class RevenueGoalMilestoneStubRepository : RevenueGoalMilestoneRepository {
    override fun findByGoalId(goalId: Long): List<RevenueGoalMilestone> = emptyList()
}
