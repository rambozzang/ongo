package com.ongo.application.auth

import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.application.common.FileStoragePort
import com.ongo.domain.accountdeletion.AccountDeletionDataPort
import com.ongo.domain.accountdeletion.AccountDeletionObjectTaskRepository
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
        /*
         * 두 단계의 실패를 절대 같은 바구니에 담지 않는다.
         *
         * DB 단계는 롤백되므로 markFailed 로 되돌려 재요청할 수 있다. 하지만 외부 정리 단계는
         * **DB 삭제가 이미 커밋된 뒤**다. 여기서 markFailed 를 쓰면 운영자가 누르는 retry 가
         * dbCommittedAt 을 지우고(AccountDeletionJobJooqRepository 의 retry) 재시작 시 DB 단계를
         * 처음부터 다시 돌린다. 사용자 행은 이미 없으므로 스캔·정책 판정이 엉뚱해지고,
         * 최악의 경우 지울 객체 목록을 잃은 채 완료로 넘어간다.
         *
         * 그래서 정리 단계의 예외는 실패가 아니라 **재시도**다. 상태를 EXTERNAL_CLEANUP_PENDING
         * 으로 되돌리고 다음 시각만 밀어 둔다 — 원장은 그대로 남아 있으므로 이어서 하면 된다.
         */
        val pending = try {
            processor.process(job)
        } catch (e: Exception) {
            // processor 트랜잭션이 먼저 롤백된 뒤 별도 트랜잭션으로 실패를 기록한다.
            log.error("계정 삭제 DB 단계 실패. jobId={} userId={}", job.id, job.userId, e)
            jobs.markFailed(
                jobId = requireNotNull(job.id),
                errorCode = "ACCOUNT_DELETION_WORKER_ERROR",
                supportReference = "worker-error:${e.javaClass.simpleName}",
            )
            return
        } ?: return

        try {
            // 커밋된 뒤에야 외부 객체를 지운다. process 는 @Transactional 이라
            // 그 안에서 지우면 롤백 시 살아있는 계정의 파일을 이미 잃은 뒤가 된다.
            processor.cleanupObjects(pending.jobId, pending.unresolvedRowCount, job.attemptCount)
        } catch (e: Exception) {
            log.error("탈퇴 외부 정리 실패 — 재시도로 되돌림. jobId={}", pending.jobId, e)
            jobs.scheduleCleanupRetry(
                pending.jobId,
                LocalDateTime.now().plusSeconds(AccountDeletionJobProcessor.cleanupBackoff(job.attemptCount)),
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
    private val objectTasks: AccountDeletionObjectTaskRepository,
    private val fileStoragePort: FileStoragePort,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * DB 단계까지만 한다. **외부 객체는 건드리지 않는다.**
     *
     * @return 커밋 뒤에 처리해야 할 정리 대상. 없으면 null.
     */
    @Transactional
    fun process(job: AccountDeletionJob): PendingCleanup? {
        /*
         * DB 단계가 이미 커밋된 job 은 다시 지우지 않는다.
         *
         * 외부 정리가 실패해 다시 집힌 경우가 여기다. 사용자 행은 이미 사라졌으므로 preflight 를
         * 다시 돌리면 스캔·정책 판정이 엉뚱한 결과를 내고, 최악의 경우 삭제를 한 번 더 시도한다.
         * 남은 일은 원장에 적힌 객체를 지우는 것뿐이고, 못 지운 행 수도 DB 에 남아 있다.
         */
        if (job.dbCommittedAt != null) {
            return PendingCleanup(requireNotNull(job.id), job.unresolvedObjectRows)
        }

        val result = AccountDeletionPreflight.evaluate(
            actualFks = scanner.actualUserFks(),
            userRowCounter = { key -> scanner.countRowsFor(key, job.userId) },
        )

        return when (result) {
            is AccountDeletionPreflight.Result.BlockedGlobally -> {
                jobs.markBlocked(
                    jobId = requireNotNull(job.id),
                    errorCode = "ACCOUNT_DELETION_BLOCKED_UNCLASSIFIED_FK",
                    supportReference = "unclassified:${result.unclassified.size}",
                )
                return null
            }

            is AccountDeletionPreflight.Result.BlockedForUser -> {
                jobs.markBlocked(
                    jobId = requireNotNull(job.id),
                    errorCode = "ACCOUNT_DELETION_BLOCKED_POLICY_REVIEW",
                    supportReference = "review-block:${result.blocking.size}",
                )
                return null
            }

            is AccountDeletionPreflight.Result.Proceed -> {
                if (subscriptionRequiresReview(job.userId)) {
                    jobs.markBlocked(
                        jobId = requireNotNull(job.id),
                        errorCode = "ACCOUNT_DELETION_BLOCKED_SUBSCRIPTION",
                        supportReference = "subscription:active-or-billing",
                    )
                    return null
                }

                log.info("계정 삭제 DB 단계 시작: jobId={} userId={} policies={}", job.id, job.userId, result.deletable.size)
                val jobId = requireNotNull(job.id)

                /*
                 * DB 삭제와 정리 원장 기록이 같은 트랜잭션으로 커밋된다. 중간 실패 시 전체가
                 * 롤백되어 재처리할 수 있고, **커밋 전에는 외부 객체를 하나도 건드리지 않는다.**
                 *
                 * DB 커밋과 외부 삭제를 원자적으로 묶는 것은 불가능하다. 스토리지는 우리
                 * 트랜잭션에 참여하지 않는다. 그래서 "먼저 지우고 커밋"이 아니라 "먼저 커밋하고
                 * 지운다"를 택했다. 전자는 롤백 시 살아있는 계정의 파일을 잃고(되돌릴 수 없다),
                 * 후자는 최악이라도 객체가 남을 뿐이며 원장이 남아 다음 tick 이 다시 지운다.
                 */
                val snapshot = deletionData.snapshotObjectsAndDeleteUserData(
                    jobId = jobId,
                    userId = job.userId,
                    policies = result.deletable,
                )

                /*
                 * 외부 삭제를 여기서 부르면 안 된다. 이 메서드는 @Transactional 이라
                 * 아직 커밋 전이고, 롤백되면 살아있는 계정의 파일을 이미 지운 뒤가 된다.
                 * 지울 목록만 돌려주고 실제 삭제는 커밋 뒤 호출자가 한다.
                 */
                return PendingCleanup(jobId, snapshot.unresolvedRowCount)
            }
        }
    }

    /**
     * 원장에 남은 객체를 지우고, **전부 지워졌을 때만** job 을 완료로 올린다.
     *
     * 건별로 성공/실패한다. 하나가 실패해도 나머지는 진행하고, 실패한 건은 PENDING 으로
     * 남아 다음 tick 이 다시 집는다. 중간에 프로세스가 죽어도 원장이 남아 있어 재개된다.
     */
    fun cleanupObjects(jobId: Long, unresolvedRowCount: Int = 0, attempt: Int = 0) {
        objectTasks.findPending(jobId, CLEANUP_BATCH_SIZE).forEach { task ->
            val taskId = task.id ?: return@forEach
            runCatching { fileStoragePort.deleteByKey(task.objectKey) }
                .onSuccess { objectTasks.markDone(taskId) }
                .onFailure { e ->
                    // 상태를 PENDING 으로 남긴다. 다음 tick 이 다시 시도한다.
                    log.warn("탈퇴 객체 삭제 실패 — 재시도 대상 [jobId={} taskId={}]", jobId, taskId, e)
                    objectTasks.markAttemptFailed(taskId, "OBJECT_DELETE_FAILED")
                }
        }

        val remaining = objectTasks.countUnfinished(jobId)
        if (remaining > 0) {
            /*
             * 아직 남았다. 다음 tick 이 이어서 처리하되, 계속 실패하는 job 하나가 매 tick 을
             * 독차지해 다른 사용자의 탈퇴를 밀어내지 않도록 시도 간격을 벌린다.
             * claimNext 는 한 번에 job 하나만 집기 때문에 이 backoff 가 없으면 기아가 생긴다.
             */
            val backoff = cleanupBackoff(attempt)
            jobs.scheduleCleanupRetry(jobId, LocalDateTime.now().plusSeconds(backoff))
            log.info("탈퇴 객체 정리 진행 중: jobId={} 남은건수={} 다음시도={}초후", jobId, remaining, backoff)
            return
        }

        /*
         * 키를 확정할 수 없는 행이 있으면 완료시키지 않는다.
         *
         * V96 이전 행은 객체 키가 없어 무엇을 지워야 하는지 알 수 없다. URL 로 추측해 지우면
         * 남의 파일을 지울 위험이 있어 하지 않는다. 그렇다고 "다 지웠다"고 표시하면 실제로는
         * 버킷에 남아 있는데 완료로 기록되는 거짓말이 된다. 사람이 보게 남긴다.
         */
        if (unresolvedRowCount > 0) {
            log.warn("탈퇴 객체 키를 확정할 수 없는 행이 있어 완료 보류: jobId={} 건수={}", jobId, unresolvedRowCount)
            jobs.markBlocked(jobId, "ACCOUNT_DELETION_OBJECT_KEY_UNRESOLVED", "objects:unresolved=$unresolvedRowCount")
            return
        }

        jobs.markCompleted(jobId)
        log.info("계정 삭제 완료: jobId={}", jobId)
    }

    private fun subscriptionRequiresReview(userId: Long): Boolean {
        val subscription = subscriptionRepository.findByUserId(userId) ?: return false
        return subscription.planType != PlanType.FREE ||
            subscription.status != SubscriptionStatus.FREE ||
            !subscription.paddleSubscriptionId.isNullOrBlank() ||
            !subscription.paddleCustomerId.isNullOrBlank()
    }

    internal companion object {
        /** 한 tick 에 처리할 객체 수. 남은 건은 다음 tick 이 이어서 집는다. */
        const val CLEANUP_BATCH_SIZE = 200

        /** 첫 실패는 다음 tick 수준(15초), 이후 두 배씩 늘려 최대 10분. */
        fun cleanupBackoff(attempt: Int): Long =
            (15L shl attempt.coerceIn(0, 6)).coerceAtMost(600L)
    }
}

/** DB 커밋 뒤에 처리해야 할 외부 정리 대상. */
data class PendingCleanup(val jobId: Long, val unresolvedRowCount: Int)
