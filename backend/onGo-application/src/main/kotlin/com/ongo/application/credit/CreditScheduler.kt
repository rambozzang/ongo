package com.ongo.application.credit

import com.ongo.common.enums.CreditTransactionType
import com.ongo.domain.credit.AiCreditTransaction
import com.ongo.domain.credit.CreditRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

@Component
class CreditScheduler(
    private val creditRepository: CreditRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val dataSource: DataSource,
) {

    private val log = LoggerFactory.getLogger(CreditScheduler::class.java)

    companion object {
        private const val LOCK_FREE_CREDIT_RESET = 100001L
        private const val LOCK_EXPIRE_PURCHASED = 100002L
        private const val LOCK_LOW_CREDIT_CHECK = 100003L
    }

    /**
     * 매월 1일 00:00 무료 크레딧 리셋
     */
    @Scheduled(cron = "0 0 0 1 * *")
    @Transactional
    fun resetFreeCredits() {
        if (!tryAdvisoryLock(LOCK_FREE_CREDIT_RESET)) {
            log.info("다른 인스턴스에서 무료 크레딧 리셋 실행 중, 스킵")
            return
        }
        try {
            val today = LocalDate.now()
            val userIds = creditRepository.findUsersForFreeReset(today)
            log.info("무료 크레딧 리셋 시작. 대상 사용자 수: {}", userIds.size)

            var successCount = 0
            for (userId in userIds) {
                try {
                    val credit = creditRepository.findByUserIdForUpdate(userId) ?: continue
                    val resetAmount = credit.freeMonthly
                    val purchasedTotal = creditRepository.findActivePurchasedCredits(userId).sumOf { it.remaining }
                    val newBalance = resetAmount + purchasedTotal

                    creditRepository.update(
                        credit.copy(
                            freeRemaining = resetAmount,
                            balance = newBalance,
                            freeResetDate = today.plusMonths(1).withDayOfMonth(1),
                            updatedAt = LocalDateTime.now(),
                        )
                    )

                    creditRepository.saveTransaction(
                        AiCreditTransaction(
                            userId = userId,
                            type = CreditTransactionType.FREE_RESET,
                            amount = resetAmount,
                            balanceAfter = newBalance,
                            feature = "MONTHLY_RESET",
                        )
                    )
                    successCount++
                } catch (e: Exception) {
                    log.error("무료 크레딧 리셋 실패. userId: {}", userId, e)
                }
            }
            log.info("무료 크레딧 리셋 완료. 성공: {}/{}", successCount, userIds.size)
        } finally {
            releaseAdvisoryLock(LOCK_FREE_CREDIT_RESET)
        }
    }

    /**
     * 매일 01:00 만료된 구매 크레딧 처리
     * Batch processes expired credits by grouping per user to minimize DB round-trips.
     */
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    fun expirePurchasedCredits() {
        if (!tryAdvisoryLock(LOCK_EXPIRE_PURCHASED)) {
            log.info("다른 인스턴스에서 만료 크레딧 처리 실행 중, 스킵")
            return
        }
        try {
            // Step 1: Bulk expire all expired purchased credits in a single UPDATE
            val expiredCount = creditRepository.bulkExpirePurchasedCredits()
            log.info("만료 크레딧 일괄 상태 변경 완료: {}건", expiredCount)

            if (expiredCount == 0) return

            // Step 2: Recalculate balances for affected users
            val affectedUserIds = creditRepository.findUsersWithExpiredCredits()
            log.info("잔액 재계산 대상 사용자 수: {}", affectedUserIds.size)

            for (userId in affectedUserIds) {
                try {
                    val credit = creditRepository.findByUserIdForUpdate(userId) ?: continue
                    val purchasedTotal = creditRepository.findActivePurchasedCredits(userId).sumOf { it.remaining }
                    val newBalance = credit.freeRemaining + purchasedTotal

                    creditRepository.update(
                        credit.copy(balance = newBalance, updatedAt = LocalDateTime.now())
                    )
                } catch (e: Exception) {
                    log.error("만료 크레딧 잔액 재계산 실패. userId: {}", userId, e)
                }
            }
            log.info("만료 크레딧 처리 완료")
        } finally {
            releaseAdvisoryLock(LOCK_EXPIRE_PURCHASED)
        }
    }

    /**
     * 매시간 잔여 크레딧 20% 이하 알림 체크
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional(readOnly = true)
    fun checkLowCreditNotifications() {
        if (!tryAdvisoryLock(LOCK_LOW_CREDIT_CHECK)) {
            log.info("다른 인스턴스에서 크레딧 부족 알림 체크 실행 중, 스킵")
            return
        }
        try {
            val lowCreditUsers = creditRepository.findLowCreditUsers()
            var notifiedCount = 0

            for (credit in lowCreditUsers) {
                log.info("크레딧 잔여 20% 이하 알림. userId: {}, balance: {}, freeMonthly: {}",
                    credit.userId, credit.balance, credit.freeMonthly)
                eventPublisher.publishEvent(
                    LowCreditAlertEvent(
                        userId = credit.userId,
                        balance = credit.balance,
                        freeMonthly = credit.freeMonthly,
                    )
                )
                notifiedCount++
            }

            if (notifiedCount > 0) {
                log.info("크레딧 부족 알림 발송: {}명", notifiedCount)
            }
        } finally {
            releaseAdvisoryLock(LOCK_LOW_CREDIT_CHECK)
        }
    }

    private fun tryAdvisoryLock(lockId: Long): Boolean = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT pg_try_advisory_lock(?)").use { stmt ->
                stmt.setLong(1, lockId)
                stmt.executeQuery().use { rs -> rs.next() && rs.getBoolean(1) }
            }
        }
    } catch (e: Exception) {
        log.warn("Advisory lock 획득 실패: lockId=$lockId", e)
        false
    }

    private fun releaseAdvisoryLock(lockId: Long) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement("SELECT pg_advisory_unlock(?)").use { stmt ->
                    stmt.setLong(1, lockId)
                    stmt.execute()
                }
            }
        } catch (e: Exception) {
            log.warn("Advisory lock 해제 실패: lockId=$lockId", e)
        }
    }
}
