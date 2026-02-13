package com.ongo.domain.credit

import java.time.LocalDate

interface CreditRepository {
    fun findByUserId(userId: Long): AiCredit?
    fun findByUserIdForUpdate(userId: Long): AiCredit?
    fun save(credit: AiCredit): AiCredit
    fun update(credit: AiCredit): AiCredit
    fun getTransactions(userId: Long, page: Int, size: Int): List<AiCreditTransaction>
    fun countTransactions(userId: Long): Long
    fun saveTransaction(transaction: AiCreditTransaction): AiCreditTransaction
    fun findActivePurchasedCredits(userId: Long): List<AiPurchasedCredit>
    fun findActivePurchasedCreditsForUpdate(userId: Long): List<AiPurchasedCredit>
    fun savePurchasedCredit(credit: AiPurchasedCredit): AiPurchasedCredit
    fun updatePurchasedCredit(credit: AiPurchasedCredit): AiPurchasedCredit
    fun findExpiredCredits(): List<AiPurchasedCredit>
    fun findUsersForFreeReset(today: LocalDate): List<Long>

    /** Bulk-expire all ACTIVE purchased credits past their expiry date. Returns count of affected rows. */
    fun bulkExpirePurchasedCredits(): Int

    /** Find user IDs that have recently expired purchased credits needing balance recalculation. */
    fun findUsersWithExpiredCredits(): List<Long>

    /** Find users whose balance is at or below 20% of their free monthly allowance (and > 0). */
    fun findLowCreditUsers(): List<AiCredit>
}
