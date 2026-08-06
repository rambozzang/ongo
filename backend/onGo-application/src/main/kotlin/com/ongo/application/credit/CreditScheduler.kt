package com.ongo.application.credit

import com.ongo.common.enums.CreditTransactionType
import com.ongo.domain.credit.AiCreditTransaction
import com.ongo.domain.credit.CreditRepository
import com.ongo.domain.lock.DistributedLockPort
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class CreditScheduler(
    private val creditRepository: CreditRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val distributedLock: DistributedLockPort,
    transactionManager: PlatformTransactionManager,
) {

    private val log = LoggerFactory.getLogger(CreditScheduler::class.java)

    /**
     * 사용자 1건을 독립 트랜잭션으로 묶는다.
     *
     * 같은 클래스 안에서 `@Transactional` 메서드를 자기호출하면 프록시를 타지 않아
     * 전파 설정이 무시된다. 그래서 애노테이션 대신 [TransactionTemplate]을 쓴다.
     */
    private val perItemTx = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    companion object {
        private const val LOCK_FREE_CREDIT_RESET = 100001L
        private const val LOCK_EXPIRE_PURCHASED = 100002L
        private const val LOCK_LOW_CREDIT_CHECK = 100003L

        /** 배치 로그의 outcome 값. 알림·집계가 문자열로 걸 수 있는 저카디널리티 값이다. */
        private const val OUTCOME_OK = "OK"
        private const val OUTCOME_PARTIAL_FAILURE = "PARTIAL_FAILURE"
        private const val OUTCOME_SKIPPED_LOCKED = "SKIPPED_LOCKED"

        /** 실패 사용자 목록을 로그에 남길 때의 상한. 전체는 건수로만 표시한다. */
        private const val FAILED_ID_LOG_LIMIT = 50
    }

    /**
     * 매월 1일 00:00 무료 크레딧 리셋
     */
    // 바깥 루프에는 트랜잭션을 두지 않는다. 사용자 1건씩 REQUIRES_NEW 로 묶고 루프 바깥에서 잡는다.
    //
    // 예전에는 이 메서드에 @Transactional 이 붙어 전체 사용자가 한 트랜잭션이었다.
    // jOOQ 가 스프링 트랜잭션에 참여하지 않던 동안에는 각 쿼리가 개별 auto-commit 이라
    // 사용자별 catch 가 실제로 "이 사용자만 건너뛰기"로 동작했다. 트랜잭션이 정상화되면서
    // 한 사용자의 DB 오류가 트랜잭션 전체를 abort 시키고, 이후 사용자가 전부 실패하며,
    // 이미 성공한 것까지 롤백되는 구조가 됐다. 사용자별 격리를 명시적으로 되돌린다.
    @Scheduled(cron = "0 0 0 1 * *")
    fun resetFreeCredits() = withAdvisoryLock(LOCK_FREE_CREDIT_RESET, "무료 크레딧 리셋") {
        val today = LocalDate.now()
        val userIds = creditRepository.findUsersForFreeReset(today)
        log.info("무료 크레딧 리셋 시작. job=freeCreditReset targets={}", userIds.size)

        val failed = mutableListOf<Long>()
        var successCount = 0
        for (userId in userIds) {
            try {
                perItemTx.executeWithoutResult { resetFreeCreditsFor(userId, today) }
                successCount++
            } catch (e: Exception) {
                failed += userId
                log.error("무료 크레딧 리셋 실패. job=freeCreditReset userId={}", userId, e)
            }
        }
        reportBatchResult("freeCreditReset", successCount, userIds.size, failed)
    }

    /** 사용자 1건의 DB 작업 전체. [perItemTx] 안에서만 호출한다. */
    private fun resetFreeCreditsFor(userId: Long, today: LocalDate) {
        val credit = creditRepository.findByUserIdForUpdate(userId) ?: return
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
    }

    /**
     * 매일 01:00 만료된 구매 크레딧 처리
     * Batch processes expired credits by grouping per user to minimize DB round-trips.
     */
    @Scheduled(cron = "0 0 1 * * *")
    fun expirePurchasedCredits() = withAdvisoryLock(LOCK_EXPIRE_PURCHASED, "만료 크레딧 처리") {
        // 1단계: 일괄 만료 처리. 단일 UPDATE 라 자체 트랜잭션으로 묶는다.
        val expiredCount = perItemTx.execute { creditRepository.bulkExpirePurchasedCredits() } ?: 0
        log.info("만료 크레딧 일괄 상태 변경 완료. job=expirePurchasedCredits expired={}", expiredCount)

        if (expiredCount > 0) {
            // 2단계: 영향받은 사용자 잔액 재계산. 사용자별 독립 트랜잭션이다.
            val affectedUserIds = creditRepository.findUsersWithExpiredCredits()
            log.info("잔액 재계산 시작. job=expirePurchasedCredits targets={}", affectedUserIds.size)

            val failed = mutableListOf<Long>()
            var successCount = 0
            for (userId in affectedUserIds) {
                try {
                    perItemTx.executeWithoutResult { recalculateBalanceFor(userId) }
                    successCount++
                } catch (e: Exception) {
                    failed += userId
                    log.error("만료 크레딧 잔액 재계산 실패. job=expirePurchasedCredits userId={}", userId, e)
                }
            }
            reportBatchResult("expirePurchasedCredits", successCount, affectedUserIds.size, failed)
        }
    }

    /** 사용자 1건의 잔액 재계산. [perItemTx] 안에서만 호출한다. */
    private fun recalculateBalanceFor(userId: Long) {
        val credit = creditRepository.findByUserIdForUpdate(userId) ?: return
        val purchasedTotal = creditRepository.findActivePurchasedCredits(userId).sumOf { it.remaining }
        val newBalance = credit.freeRemaining + purchasedTotal

        creditRepository.update(credit.copy(balance = newBalance, updatedAt = LocalDateTime.now()))
    }

    /**
     * 매시간 잔여 크레딧 20% 이하 알림 체크
     */
    // readOnly 경로라 트랜잭션 구조는 그대로 둔다. 잠금 처리만 고친 helper 를 태운다.
    @Scheduled(cron = "0 0 * * * *")
    @Transactional(readOnly = true)
    fun checkLowCreditNotifications() = withAdvisoryLock(LOCK_LOW_CREDIT_CHECK, "크레딧 부족 알림 체크") {
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
    }

    /** 공유 락 구현에 위임한다. 잡지 못하면 건너뛴 사실을 남긴다. */
    private fun withAdvisoryLock(lockId: Long, jobName: String, block: () -> Unit) {
        val ran = distributedLock.withLock(lockId, block)
        if (!ran) {
            log.info(
                "다른 인스턴스에서 실행 중이라 건너뛴다. job={} lockId={} outcome={}",
                jobName, lockId, OUTCOME_SKIPPED_LOCKED,
            )
        }
    }

    /** 배치 결과를 알림 가능한 형태로 표면화한다. 조용한 전체 실패보다 낫다. */
    private fun reportBatchResult(job: String, success: Int, total: Int, failed: List<Long>) {
        if (failed.isEmpty()) {
            log.info("배치 완료. job={} success={} total={} outcome={}", job, success, total, OUTCOME_OK)
            return
        }
        val shown = failed.take(FAILED_ID_LOG_LIMIT)
        val suffix = if (failed.size > shown.size) " 외 ${failed.size - shown.size}건" else ""
        log.error(
            "배치 일부 실패. job={} success={} failed={} total={} outcome={} failedUserIds={}{}",
            job, success, failed.size, total, OUTCOME_PARTIAL_FAILURE, shown, suffix,
        )
    }
}
