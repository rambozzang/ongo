package com.ongo.domain.fanfunding

interface FundingTransactionRepository {
    fun findById(id: Long): FundingTransaction?
    fun findByUserId(userId: Long): List<FundingTransaction>
    fun findByUserIdAndPeriod(userId: Long, days: Int): List<FundingTransaction>
    fun findByUserIdAndFilter(userId: Long, source: String?, platform: String?, dateFrom: String?, dateTo: String?): List<FundingTransaction>
    fun save(transaction: FundingTransaction): FundingTransaction
    fun delete(id: Long)
}
