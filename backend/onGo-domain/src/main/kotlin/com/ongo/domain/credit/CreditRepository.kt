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

    /**
     * id 로 구매 크레딧 행을 잠그고 가져온다. **상태·만료를 가리지 않는다.**
     *
     * 정확한 환불에 필요하다. 차감으로 소진된 패키지는 `EXHAUSTED` 가 되어
     * [findActivePurchasedCreditsForUpdate] 에 잡히지 않는데, 되돌려야 할 곳이 바로 그
     * 행이다. 상태로 거르면 방금 다 쓴 패키지로는 환불할 수 없다.
     *
     * [userId] 를 함께 받아 다른 사용자의 행을 건드리지 못하게 한다.
     */
    fun findPurchasedCreditsByIdsForUpdate(userId: Long, ids: Collection<Long>): List<AiPurchasedCredit>
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
