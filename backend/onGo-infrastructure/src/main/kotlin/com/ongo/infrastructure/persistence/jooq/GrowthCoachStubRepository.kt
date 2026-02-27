package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.growthcoach.*
import org.springframework.stereotype.Repository

@Repository
class GrowthCoachStubRepository : GrowthCoachRepository {
    override fun findGoalById(id: Long): GrowthGoal? = null
    override fun findGoalsByUserId(userId: Long): List<GrowthGoal> = emptyList()
    override fun saveGoal(goal: GrowthGoal): GrowthGoal = goal.copy(id = 1)
    override fun updateGoal(goal: GrowthGoal): GrowthGoal = goal
    override fun deleteGoal(id: Long) {}
    override fun findReportById(id: Long): WeeklyReport? = null
    override fun findReportsByUserId(userId: Long): List<WeeklyReport> = emptyList()
    override fun saveReport(report: WeeklyReport): WeeklyReport = report.copy(id = 1)
    override fun updateReport(report: WeeklyReport): WeeklyReport = report
    override fun deleteReport(id: Long) {}
}
