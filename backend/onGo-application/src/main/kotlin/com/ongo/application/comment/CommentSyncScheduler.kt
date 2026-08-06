package com.ongo.application.comment

import com.ongo.common.enums.PlanType
import com.ongo.domain.lock.DistributedLockPort
import com.ongo.domain.subscription.SubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class CommentSyncScheduler(
    private val commentSyncUseCase: CommentSyncUseCase,
    private val subscriptionRepository: SubscriptionRepository,
    private val distributedLockPort: DistributedLockPort,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val lockId = javaClass.name.hashCode().toLong()

    /**
     * 매 5분마다 Pro/Business 사용자의 댓글을 자동 동기화합니다.
     */
    @Scheduled(cron = "0 */5 * * * *")
    fun syncCommentsForActiveUsers() {
        // tryLock/releaseLock 은 획득과 해제가 다른 커넥션에서 일어나 락이 누수된다.
        // PostgreSQL 자문 락은 세션 범위라 다른 커넥션에서 해제해도 풀리지 않는다.
        val ran = distributedLockPort.withLock(lockId) { syncAllUsersComments() }
        if (!ran) log.debug("다른 인스턴스에서 댓글 동기화 실행 중, 스킵")
    }

    private fun syncAllUsersComments() {
        log.info("댓글 자동 동기화 스케줄러 시작")

        val eligiblePlans = setOf(PlanType.PRO, PlanType.BUSINESS)
        val subscriptions = eligiblePlans.flatMap { planType ->
            try {
                subscriptionRepository.findByPlanType(planType)
            } catch (e: Exception) {
                log.warn("구독 조회 실패 (plan={}): {}", planType, e.message)
                emptyList()
            }
        }

        var successCount = 0
        var failCount = 0

        for (subscription in subscriptions) {
            try {
                val result = commentSyncUseCase.syncAllComments(subscription.userId)
                log.debug("댓글 동기화 완료: userId={}, synced={}, new={}",
                    subscription.userId, result.totalSynced, result.totalNew)
                successCount++
            } catch (e: Exception) {
                log.warn("댓글 동기화 실패: userId={}, error={}", subscription.userId, e.message)
                failCount++
            }
        }

        log.info("댓글 자동 동기화 스케줄러 완료: 성공={}, 실패={}", successCount, failCount)
    }
}
