package com.ongo.domain.fanfunding

interface FundingGoalRepository {
    fun findById(id: Long): FundingGoal?
    fun findByUserId(userId: Long): List<FundingGoal>
    fun save(goal: FundingGoal): FundingGoal
    fun update(goal: FundingGoal): FundingGoal
    fun delete(id: Long)
}
