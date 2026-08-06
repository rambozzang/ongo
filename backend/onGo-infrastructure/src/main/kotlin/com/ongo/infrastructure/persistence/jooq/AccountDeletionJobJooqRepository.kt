package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.accountdeletion.AccountDeletionJob
import com.ongo.domain.accountdeletion.AccountDeletionJobRepository
import com.ongo.domain.accountdeletion.AccountDeletionState
import com.ongo.domain.accountdeletion.AccountDeletionStatus
import com.ongo.infrastructure.persistence.jooq.Fields.ATTEMPT_COUNT
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

    override fun findActiveByUserId(userId: Long): AccountDeletionJob? =
        dsl.select()
            .from(ACCOUNT_DELETION_JOBS)
            .where(USER_ID.eq(userId))
            .and(STATUS.`in`(activeStatuses))
            .fetchOne()
            ?.toJob()

    override fun findByIdempotencyKey(key: String): AccountDeletionJob? =
        dsl.select()
            .from(ACCOUNT_DELETION_JOBS)
            .where(IDEMPOTENCY_KEY.eq(key))
            .fetchOne()
            ?.toJob()

    override fun findDeletionState(userId: Long): AccountDeletionState? =
        dsl.select(DELETION_STATE)
            .from(USERS)
            .where(ID.eq(userId))
            .fetchOne()
            ?.get(DELETION_STATE)
            ?.let { AccountDeletionState.valueOf(it) }

    private fun findById(id: Long): AccountDeletionJob? =
        dsl.select()
            .from(ACCOUNT_DELETION_JOBS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toJob()

    private fun Record.toJob() = AccountDeletionJob(
        id = get(ID),
        userId = get(USER_ID),
        status = AccountDeletionStatus.valueOf(get(STATUS)),
        idempotencyKey = get(IDEMPOTENCY_KEY),
        supportReference = get(SUPPORT_REFERENCE),
        attemptCount = get(ATTEMPT_COUNT) ?: 0,
        lastErrorCode = get(LAST_ERROR_CODE),
        requestedAt = localDateTime(REQUESTED_AT),
        updatedAt = localDateTime(UPDATED_AT),
        dbCommittedAt = localDateTime(DB_COMMITTED_AT),
        completedAt = localDateTime(COMPLETED_AT),
    )
}
