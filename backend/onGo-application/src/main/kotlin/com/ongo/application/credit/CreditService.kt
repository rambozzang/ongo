package com.ongo.application.credit

import com.ongo.common.enums.AiFeature
import com.ongo.common.enums.CreditTransactionType
import com.ongo.common.enums.PlanType
import com.ongo.common.exception.CreditNotFoundException
import com.ongo.common.exception.InsufficientCreditException
import com.ongo.domain.credit.AiCredit
import com.ongo.domain.credit.AiCreditTransaction
import com.ongo.domain.credit.CreditRepository
import com.ongo.domain.event.CreditDeductedEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class CreditService(
    private val creditRepository: CreditRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {

    @Transactional
    fun validateAndDeduct(userId: Long, feature: AiFeature) {
        validateAndDeduct(userId, feature.creditCost, feature.name)
    }

    @Transactional
    fun validateAndDeduct(userId: Long, amount: Int, featureName: String) {
        // 1. 비관적 락으로 크레딧 조회 (FOR UPDATE)
        val credit = creditRepository.findByUserIdForUpdate(userId)
            ?: throw CreditNotFoundException(userId)

        // 총 가용 크레딧 확인
        val purchasedCredits = creditRepository.findActivePurchasedCreditsForUpdate(userId)
        val totalAvailable = credit.freeRemaining + purchasedCredits.sumOf { it.remaining }

        if (totalAvailable < amount) {
            throw InsufficientCreditException(amount, totalAvailable)
        }

        var remaining = amount

        // 2. 무료 크레딧 우선 차감
        var freeDeducted = 0
        if (credit.freeRemaining > 0 && remaining > 0) {
            freeDeducted = minOf(credit.freeRemaining, remaining)
            remaining -= freeDeducted
        }

        // 3. 구매 크레딧 차감 (만료 임박순 FIFO - expiresAt ASC 정렬)
        if (remaining > 0) {
            for (pkg in purchasedCredits) {
                if (remaining <= 0) break
                val deduct = minOf(pkg.remaining, remaining)
                creditRepository.updatePurchasedCredit(
                    pkg.copy(
                        remaining = pkg.remaining - deduct,
                        status = if (pkg.remaining - deduct == 0) "EXHAUSTED" else pkg.status,
                    )
                )
                remaining -= deduct
            }
        }

        // 4. 잔액 계산 및 갱신
        val newFreeRemaining = credit.freeRemaining - freeDeducted
        val newPurchasedTotal = creditRepository.findActivePurchasedCredits(userId).sumOf { it.remaining }
        val newBalance = newFreeRemaining + newPurchasedTotal

        creditRepository.update(
            credit.copy(
                freeRemaining = newFreeRemaining,
                balance = newBalance,
                updatedAt = LocalDateTime.now(),
            )
        )

        // 5. 트랜잭션 기록
        creditRepository.saveTransaction(
            AiCreditTransaction(
                userId = userId,
                type = CreditTransactionType.DEDUCT,
                amount = -amount,
                balanceAfter = newBalance,
                feature = featureName,
            )
        )

        // 6. 잔여 크레딧 이벤트 발행 (알림용)
        eventPublisher.publishEvent(
            CreditDeductedEvent(
                userId = userId,
                amount = amount,
                feature = AiFeature.entries.find { it.name == featureName } ?: AiFeature.META_GENERATION,
                remainingBalance = newBalance,
            )
        )
    }

    @Transactional(readOnly = true)
    fun getBalance(userId: Long): CreditBalanceInfo {
        val credit = creditRepository.findByUserId(userId)
            ?: return CreditBalanceInfo(0, 0, 0, 0, LocalDate.now().withDayOfMonth(1).plusMonths(1))

        val purchasedTotal = creditRepository.findActivePurchasedCredits(userId).sumOf { it.remaining }
        return CreditBalanceInfo(
            totalBalance = credit.freeRemaining + purchasedTotal,
            freeRemaining = credit.freeRemaining,
            freeMonthly = credit.freeMonthly,
            purchasedBalance = purchasedTotal,
            freeResetDate = credit.freeResetDate,
        )
    }

    @Transactional
    fun initializeCredits(userId: Long, planType: PlanType) {
        val nextMonth = LocalDate.now().withDayOfMonth(1).plusMonths(1)
        val credit = creditRepository.save(
            AiCredit(
                userId = userId,
                balance = planType.freeCredits,
                freeMonthly = planType.freeCredits,
                freeRemaining = planType.freeCredits,
                freeResetDate = nextMonth,
            )
        )

        creditRepository.saveTransaction(
            AiCreditTransaction(
                userId = userId,
                type = CreditTransactionType.FREE_RESET,
                amount = planType.freeCredits,
                balanceAfter = credit.balance,
                feature = "PLAN_INIT",
            )
        )
    }

    @Transactional
    fun refundCredit(userId: Long, amount: Int, featureName: String) {
        val credit = creditRepository.findByUserIdForUpdate(userId)
            ?: throw CreditNotFoundException(userId)

        val newFreeRemaining = minOf(credit.freeRemaining + amount, credit.freeMonthly)
        val refundedToFree = newFreeRemaining - credit.freeRemaining
        val newBalance = credit.balance + amount

        creditRepository.update(
            credit.copy(
                freeRemaining = newFreeRemaining,
                balance = newBalance,
                updatedAt = LocalDateTime.now(),
            )
        )

        creditRepository.saveTransaction(
            AiCreditTransaction(
                userId = userId,
                type = CreditTransactionType.REFUND,
                amount = amount,
                balanceAfter = newBalance,
                feature = featureName,
            )
        )
    }

    @Transactional
    fun revokeCredits(userId: Long, amount: Int, reason: String) {
        val credit = creditRepository.findByUserIdForUpdate(userId)
            ?: throw CreditNotFoundException(userId)

        val newBalance = (credit.balance - amount).coerceAtLeast(0)
        val newFreeRemaining = minOf(credit.freeRemaining, newBalance)

        creditRepository.update(
            credit.copy(
                balance = newBalance,
                freeRemaining = newFreeRemaining,
                updatedAt = LocalDateTime.now(),
            )
        )

        creditRepository.saveTransaction(
            AiCreditTransaction(
                userId = userId,
                type = CreditTransactionType.REVOKE,
                amount = -amount,
                balanceAfter = newBalance,
                feature = reason,
            )
        )
    }

    @Transactional
    fun addPurchasedCredits(userId: Long, credits: Int, referenceId: Long) {
        val credit = creditRepository.findByUserIdForUpdate(userId)
            ?: throw CreditNotFoundException(userId)

        val newBalance = credit.balance + credits
        creditRepository.update(
            credit.copy(balance = newBalance, updatedAt = LocalDateTime.now())
        )

        creditRepository.saveTransaction(
            AiCreditTransaction(
                userId = userId,
                type = CreditTransactionType.CHARGE,
                amount = credits,
                balanceAfter = newBalance,
                referenceId = referenceId,
            )
        )
    }
}

data class CreditBalanceInfo(
    val totalBalance: Int,
    val freeRemaining: Int,
    val freeMonthly: Int,
    val purchasedBalance: Int,
    val freeResetDate: LocalDate,
)
