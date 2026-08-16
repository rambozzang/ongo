package com.ongo.application.auth

import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.domain.accountdeletion.AccountDeletionDataPort
import com.ongo.domain.accountdeletion.AccountDeletionJob
import com.ongo.domain.accountdeletion.AccountDeletionJobRepository
import com.ongo.domain.accountdeletion.AccountDeletionPreflight
import com.ongo.domain.accountdeletion.UserFkScanner
import com.ongo.domain.subscription.SubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 계정 삭제 durable job 을 선점해 처리하는 워커.
 *
 * 정책 점검은 요청 시점과 워커 실행 시점에 모두 수행한다. 요청 후 운영자가 새 정책을
 * 추가했거나 스키마가 바뀌어도, 이미 동결된 계정에서 삭제가 fail-open 되지 않게 하기 위해서다.
 */
@Component
class AccountDeletionWorker(
    private val jobs: AccountDeletionJobRepository,
    private val processor: AccountDeletionJobProcessor,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${account-deletion.worker-delay-ms:15000}")
    fun processNext() {
        val now = LocalDateTime.now()
        val job = jobs.claimNext(now, now.minusMinutes(30)) ?: return
        try {
            processor.process(job)
        } catch (e: Exception) {
            // processor 트랜잭션이 먼저 롤백된 뒤 별도 트랜잭션으로 실패를 기록한다.
            log.error("계정 삭제 job 처리 실패. jobId={} userId={}", job.id, job.userId, e)
            jobs.markFailed(
                jobId = requireNotNull(job.id),
                errorCode = "ACCOUNT_DELETION_WORKER_ERROR",
                supportReference = "worker-error:${e.javaClass.simpleName}",
            )
        }
    }
}

/** 삭제 DB 트랜잭션을 워커의 선점/실패 기록과 분리해 원자성을 보장한다. */
@Component
class AccountDeletionJobProcessor(
    private val jobs: AccountDeletionJobRepository,
    private val scanner: UserFkScanner,
    private val deletionData: AccountDeletionDataPort,
    private val subscriptionRepository: SubscriptionRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun process(job: AccountDeletionJob) {
        val result = AccountDeletionPreflight.evaluate(
            actualFks = scanner.actualUserFks(),
            userRowCounter = { key -> scanner.countRowsFor(key, job.userId) },
        )

        when (result) {
            is AccountDeletionPreflight.Result.BlockedGlobally -> {
                jobs.markBlocked(
                    jobId = requireNotNull(job.id),
                    errorCode = "ACCOUNT_DELETION_BLOCKED_UNCLASSIFIED_FK",
                    supportReference = "unclassified:${result.unclassified.size}",
                )
                return
            }

            is AccountDeletionPreflight.Result.BlockedForUser -> {
                jobs.markBlocked(
                    jobId = requireNotNull(job.id),
                    errorCode = "ACCOUNT_DELETION_BLOCKED_POLICY_REVIEW",
                    supportReference = "review-block:${result.blocking.size}",
                )
                return
            }

            is AccountDeletionPreflight.Result.Proceed -> {
                if (subscriptionRequiresReview(job.userId)) {
                    jobs.markBlocked(
                        jobId = requireNotNull(job.id),
                        errorCode = "ACCOUNT_DELETION_BLOCKED_SUBSCRIPTION",
                        supportReference = "subscription:active-or-billing",
                    )
                    return
                }

                log.info("계정 삭제 DB 단계 시작: jobId={} userId={} policies={}", job.id, job.userId, result.deletable.size)
                // 이 호출 안에서 사용자 소유 row 삭제와 COMPLETED 기록이 같은 트랜잭션으로
                // 커밋된다. 중간 실패 시 전체가 롤백되어 재처리할 수 있다.
                deletionData.deleteUserDataAndComplete(
                    jobId = requireNotNull(job.id),
                    userId = job.userId,
                    policies = result.deletable,
                )
            }
        }
    }

    private fun subscriptionRequiresReview(userId: Long): Boolean {
        val subscription = subscriptionRepository.findByUserId(userId) ?: return false
        return subscription.planType != PlanType.FREE ||
            subscription.status != SubscriptionStatus.FREE ||
            !subscription.paddleSubscriptionId.isNullOrBlank() ||
            !subscription.paddleCustomerId.isNullOrBlank()
    }
}
