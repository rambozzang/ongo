package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.fanfunding.*
import org.springframework.stereotype.Repository

@Repository
class FundingGoalStubRepository : FundingGoalRepository {
    override fun findById(id: Long): FundingGoal? = null
    override fun findByUserId(userId: Long): List<FundingGoal> = emptyList()
    override fun save(goal: FundingGoal): FundingGoal = goal.copy(id = 1)
    override fun update(goal: FundingGoal): FundingGoal = goal
    override fun delete(id: Long) {}
}

@Repository
class FundingTransactionStubRepository : FundingTransactionRepository {
    override fun findById(id: Long): FundingTransaction? = null
    override fun findByUserId(userId: Long): List<FundingTransaction> = emptyList()
    override fun findByUserIdAndPeriod(userId: Long, days: Int): List<FundingTransaction> = emptyList()
    override fun findByUserIdAndFilter(userId: Long, source: String?, platform: String?, dateFrom: String?, dateTo: String?): List<FundingTransaction> = emptyList()
    override fun save(transaction: FundingTransaction): FundingTransaction = transaction.copy(id = 1)
    override fun delete(id: Long) {}
}
