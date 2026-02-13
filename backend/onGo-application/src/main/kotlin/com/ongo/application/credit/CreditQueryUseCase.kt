package com.ongo.application.credit

import com.ongo.common.config.PageResponse
import com.ongo.common.enums.CreditTransactionType
import com.ongo.domain.credit.CreditRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class CreditQueryUseCase(
    private val creditRepository: CreditRepository,
) {

    fun getBalance(userId: Long): CreditBalanceResult {
        val credit = creditRepository.findByUserId(userId)
            ?: return CreditBalanceResult(
                totalBalance = 0,
                freeRemaining = 0,
                freeMonthly = 0,
                purchasedBalance = 0,
                freeResetDate = LocalDate.now().withDayOfMonth(1).plusMonths(1),
            )

        val purchasedTotal = creditRepository.findActivePurchasedCredits(userId).sumOf { it.remaining }
        return CreditBalanceResult(
            totalBalance = credit.freeRemaining + purchasedTotal,
            freeRemaining = credit.freeRemaining,
            freeMonthly = credit.freeMonthly,
            purchasedBalance = purchasedTotal,
            freeResetDate = credit.freeResetDate,
        )
    }

    fun getTransactions(userId: Long, page: Int, size: Int): PageResponse<CreditTransactionResult> {
        val transactions = creditRepository.getTransactions(userId, page, size)
        val totalElements = creditRepository.countTransactions(userId)

        val items = transactions.map { tx ->
            CreditTransactionResult(
                id = tx.id!!,
                type = tx.type,
                amount = tx.amount,
                balanceAfter = tx.balanceAfter,
                feature = tx.feature,
                createdAt = tx.createdAt,
            )
        }

        return PageResponse.of(items, page, size, totalElements)
    }
}

data class CreditBalanceResult(
    val totalBalance: Int,
    val freeRemaining: Int,
    val freeMonthly: Int,
    val purchasedBalance: Int,
    val freeResetDate: LocalDate,
)

data class CreditTransactionResult(
    val id: Long,
    val type: CreditTransactionType,
    val amount: Int,
    val balanceAfter: Int,
    val feature: String?,
    val createdAt: LocalDateTime?,
)
