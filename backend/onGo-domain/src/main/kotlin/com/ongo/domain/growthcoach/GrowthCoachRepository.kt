package com.ongo.domain.growthcoach

interface GrowthCoachRepository {
    fun findGoalById(id: Long): GrowthGoal?
    fun findGoalsByUserId(userId: Long): List<GrowthGoal>
    fun saveGoal(goal: GrowthGoal): GrowthGoal
    fun updateGoal(goal: GrowthGoal): GrowthGoal
    fun deleteGoal(id: Long)
    fun findReportById(id: Long): WeeklyReport?
    fun findReportsByUserId(userId: Long): List<WeeklyReport>
    fun saveReport(report: WeeklyReport): WeeklyReport
    fun updateReport(report: WeeklyReport): WeeklyReport
    fun deleteReport(id: Long)
}
