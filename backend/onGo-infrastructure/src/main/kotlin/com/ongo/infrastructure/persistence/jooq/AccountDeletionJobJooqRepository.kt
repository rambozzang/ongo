package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.accountdeletion.AccountDeletionJob
import com.ongo.domain.accountdeletion.AccountDeletionJobRepository
import com.ongo.domain.accountdeletion.AccountDeletionState
import com.ongo.domain.accountdeletion.AccountDeletionStatus
import com.ongo.infrastructure.persistence.jooq.Fields.ACCOUNT_ATTEMPT_COUNT
import com.ongo.infrastructure.persistence.jooq.Fields.COMPLETED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DB_COMMITTED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DELETION_REQUESTED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DELETION_STATE
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.IDEMPOTENCY_KEY
import com.ongo.infrastructure.persistence.jooq.Fields.LAST_ERROR_CODE
import com.ongo.infrastructure.persistence.jooq.Fields.REQUESTED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.SUPPORT_REFERENCE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.ACCOUNT_DELETION_JOBS
import com.ongo.infrastructure.persistence.jooq.Tables.USERS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Repository
class AccountDeletionJobJooqRepository(
    private val dsl: DSLContext,
) : AccountDeletionJobRepository {

    /** 진행 중으로 보는 상태. 부분 유일 인덱스의 조건과 반드시 같아야 한다. */
    private val activeStatuses = listOf(
        AccountDeletionStatus.REQUESTED,
        AccountDeletionStatus.IN_PROGRESS,
        AccountDeletionStatus.DB_COMMITTED,
        AccountDeletionStatus.EXTERNAL_CLEANUP_PENDING,
    ).map { it.name }

    @Transactional
    override fun claimNext(now: LocalDateTime, staleBefore: LocalDateTime): AccountDeletionJob? =
        dsl.fetchOne(
            """
            WITH candidate AS (
                SELECT id
                FROM account_deletion_jobs
                WHERE status = 'REQUESTED'
                   OR (status = 'IN_PROGRESS' AND updated_at < ?)
                ORDER BY requested_at, id
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            UPDATE account_deletion_jobs job
               SET status = 'IN_PROGRESS',
                   attempt_count = job.attempt_count + 1,
                   updated_at = ?
              FROM candidate
             WHERE job.id = candidate.id
            RETURNING job.*
            """.trimIndent(),
            staleBefore,
            now,
        )?.toJob()

    /**
     * 사용자 행을 잠근 뒤 게이트 전환과 job 생성을 **같은 트랜잭션**에서 처리한다.
     *
     * 행 잠금이 없으면 두 요청이 동시에 "진행 중 job 없음"을 보고 둘 다 insert 를 시도한다.
     * 부분 유일 인덱스가 한쪽을 막아주긴 하지만, 그러면 한쪽은 예외로 실패한다.
     * 잠금을 먼저 잡으면 뒤에 온 요청이 앞선 job 을 보고 그대로 돌려준다 — 멱등해진다.
     */
    @Transactional
    override fun requestDeletion(userId: Long, idempotencyKey: String): AccountDeletionJob {
        // 사용자 행 잠금. 게이트와 job 을 한 덩어리로 다루기 위한 직렬화 지점이다.
        dsl.select(ID)
            .from(USERS)
            .where(ID.eq(userId))
            .forUpdate()
            .fetchOne() ?: throw IllegalArgumentException("사용자를 찾을 수 없다: $userId")

        findActiveByUserId(userId)?.let { return it }

        val now = LocalDateTime.now()
        val id = dsl.insertInto(ACCOUNT_DELETION_JOBS)
            .set(USER_ID, userId)
            .set(STATUS, AccountDeletionStatus.REQUESTED.name)
            .set(IDEMPOTENCY_KEY, idempotencyKey)
            .set(REQUESTED_AT, now)
            .set(UPDATED_AT, now)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        // 게이트를 켠다. 이 갱신이 위 insert 와 같은 트랜잭션이라
        // "job 은 있는데 쓰기가 계속 허용" 되는 구간이 생기지 않는다.
        dsl.update(USERS)
            .set(DELETION_STATE, AccountDeletionState.DELETION_REQUESTED.name)
            .set(DELETION_REQUESTED_AT, now)
            .where(ID.eq(userId))
            .execute()

        return findById(id)!!
    }

    /**
     * 막힌 시도를 종료 상태로 남긴다. **게이트를 건드리지 않는다.**
     *
     * 여기서 `deletion_state` 를 켜면 정책 판단이 끝날 때까지 계정이 잠긴다. 그건
     * 삭제를 못 하게 하는 것을 넘어 계정을 못 쓰게 만드는 것이라 훨씬 나쁘다.
     *
     * 종료 상태라 부분 유일 인덱스에 걸리지 않는다. 같은 사용자가 여러 번 막혀도
     * 기록이 쌓이고, 나중에 정책이 정해지면 그대로 재요청할 수 있다.
     */
    @Transactional
    override fun recordBlocked(
        userId: Long,
        idempotencyKey: String,
        errorCode: String,
        supportReference: String?,
    ): AccountDeletionJob {
        findByIdempotencyKey(idempotencyKey)?.let { return it }

        val now = LocalDateTime.now()
        val id = dsl.insertInto(ACCOUNT_DELETION_JOBS)
            .set(USER_ID, userId)
            .set(STATUS, AccountDeletionStatus.BLOCKED_POLICY.name)
            .set(IDEMPOTENCY_KEY, idempotencyKey)
            .set(LAST_ERROR_CODE, errorCode)
            .set(SUPPORT_REFERENCE, supportReference)
            .set(REQUESTED_AT, now)
            .set(UPDATED_AT, now)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    override fun findActiveByUserId(userId: Long): AccountDeletionJob? =
        dsl.select()
            .from(ACCOUNT_DELETION_JOBS)
            .where(USER_ID.eq(userId))
            .and(STATUS.`in`(activeStatuses))
            .fetchOne()
            ?.toJob()

    override fun findLatestByUserId(userId: Long): AccountDeletionJob? =
        dsl.select()
            .from(ACCOUNT_DELETION_JOBS)
            .where(USER_ID.eq(userId))
            .orderBy(REQUESTED_AT.desc(), ID.desc())
            .limit(1)
            .fetchOne()
            ?.toJob()

    override fun findByIdempotencyKey(key: String): AccountDeletionJob? =
        dsl.select()
            .from(ACCOUNT_DELETION_JOBS)
            .where(IDEMPOTENCY_KEY.eq(key))
            .fetchOne()
            ?.toJob()

    override fun findRecent(limit: Int): List<AccountDeletionJob> =
        dsl.select()
            .from(ACCOUNT_DELETION_JOBS)
            .orderBy(UPDATED_AT.desc(), ID.desc())
            .limit(limit.coerceIn(1, 500))
            .fetch()
            .map { it.toJob() }

    @Transactional
    override fun retry(jobId: Long): AccountDeletionJob? {
        // 상태 확인과 전환 사이에 worker가 선점하지 못하도록 job 자체를 먼저 잠근다.
        val job = dsl.select()
            .from(ACCOUNT_DELETION_JOBS)
            .where(ID.eq(jobId))
            .forUpdate()
            .fetchOne()
            ?.toJob() ?: return null
        if (job.status != AccountDeletionStatus.FAILED && job.status != AccountDeletionStatus.BLOCKED_POLICY) {
            return null
        }

        // 재처리 전 사용자 행을 잠근다. 사용자가 이미 사라졌다면 재시도하지 않는다.
        dsl.select(ID)
            .from(USERS)
            .where(ID.eq(job.userId))
            .forUpdate()
            .fetchOne() ?: return null

        val now = LocalDateTime.now()
        dsl.update(ACCOUNT_DELETION_JOBS)
            .set(STATUS, AccountDeletionStatus.REQUESTED.name)
            .set(LAST_ERROR_CODE, null as String?)
            .set(SUPPORT_REFERENCE, null as String?)
            .set(DB_COMMITTED_AT, null as LocalDateTime?)
            .set(COMPLETED_AT, null as LocalDateTime?)
            .set(UPDATED_AT, now)
            .where(ID.eq(jobId))
            .and(STATUS.`in`(AccountDeletionStatus.FAILED.name, AccountDeletionStatus.BLOCKED_POLICY.name))
            .execute()
        dsl.update(USERS)
            .set(DELETION_STATE, AccountDeletionState.DELETION_REQUESTED.name)
            .set(DELETION_REQUESTED_AT, now)
            .where(ID.eq(job.userId))
            .execute()
        return findById(jobId)
    }

    @Transactional
    override fun markBlocked(jobId: Long, errorCode: String, supportReference: String?): AccountDeletionJob? {
        val userId = dsl.select(USER_ID)
            .from(ACCOUNT_DELETION_JOBS)
            .where(ID.eq(jobId))
            .forUpdate()
            .fetchOne()
            ?.let { it.get(USER_ID) as Number }
            ?.toLong()
            ?: return null

        val now = LocalDateTime.now()
        dsl.update(ACCOUNT_DELETION_JOBS)
            .set(STATUS, AccountDeletionStatus.BLOCKED_POLICY.name)
            .set(LAST_ERROR_CODE, errorCode)
            .set(SUPPORT_REFERENCE, supportReference)
            .set(UPDATED_AT, now)
            .where(ID.eq(jobId))
            .execute()
        reactivateUser(userId)
        return findById(jobId)
    }

    @Transactional
    override fun markCompleted(jobId: Long, completedAt: LocalDateTime): AccountDeletionJob? {
        dsl.update(ACCOUNT_DELETION_JOBS)
            .set(STATUS, AccountDeletionStatus.COMPLETED.name)
            .set(DB_COMMITTED_AT, completedAt)
            .set(COMPLETED_AT, completedAt)
            .set(UPDATED_AT, completedAt)
            .where(ID.eq(jobId))
            .execute()
        return findById(jobId)
    }

    @Transactional
    override fun markFailed(jobId: Long, errorCode: String, supportReference: String?): AccountDeletionJob? {
        val userId = dsl.select(USER_ID)
            .from(ACCOUNT_DELETION_JOBS)
            .where(ID.eq(jobId))
            .forUpdate()
            .fetchOne()
            ?.let { it.get(USER_ID) as Number }
            ?.toLong()
            ?: return null

        val now = LocalDateTime.now()
        dsl.update(ACCOUNT_DELETION_JOBS)
            .set(STATUS, AccountDeletionStatus.FAILED.name)
            .set(LAST_ERROR_CODE, errorCode)
            .set(SUPPORT_REFERENCE, supportReference)
            .set(UPDATED_AT, now)
            .execute()
        // 실패한 삭제는 데이터가 롤백된 상태이므로 사용자가 다시 요청할 수 있어야 한다.
        reactivateUser(userId)
        return findById(jobId)
    }

    override fun findDeletionState(userId: Long): AccountDeletionState? =
        dsl.select(DELETION_STATE)
            .from(USERS)
            .where(ID.eq(userId))
            .fetchOne()
            ?.get(DELETION_STATE)
            ?.let { AccountDeletionState.valueOf(it) }

    override fun findById(jobId: Long): AccountDeletionJob? =
        dsl.select()
            .from(ACCOUNT_DELETION_JOBS)
            .where(ID.eq(jobId))
            .fetchOne()
            ?.toJob()

    private fun reactivateUser(userId: Long) {
        dsl.update(USERS)
            .set(DELETION_STATE, AccountDeletionState.ACTIVE.name)
            .set(DELETION_REQUESTED_AT, null as LocalDateTime?)
            .where(ID.eq(userId))
            .and(DELETION_STATE.eq(AccountDeletionState.DELETION_REQUESTED.name))
            .execute()
    }

    private fun Record.toJob() = AccountDeletionJob(
        id = (get(ID) as Number).toLong(),
        userId = (get(USER_ID) as Number).toLong(),
        status = AccountDeletionStatus.valueOf(get(STATUS)),
        idempotencyKey = get(IDEMPOTENCY_KEY),
        supportReference = get(SUPPORT_REFERENCE),
        attemptCount = get(ACCOUNT_ATTEMPT_COUNT) ?: 0,
        lastErrorCode = get(LAST_ERROR_CODE),
        requestedAt = localDateTime(REQUESTED_AT),
        updatedAt = localDateTime(UPDATED_AT),
        dbCommittedAt = localDateTime(DB_COMMITTED_AT),
        completedAt = localDateTime(COMPLETED_AT),
    )
}
