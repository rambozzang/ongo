package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.accountdeletion.AccountDeletionObjectTask
import com.ongo.domain.accountdeletion.AccountDeletionObjectTaskRepository
import com.ongo.domain.accountdeletion.ObjectCleanupStatus
import com.ongo.infrastructure.persistence.jooq.Fields.CLEANUP_ATTEMPT_COUNT
import com.ongo.infrastructure.persistence.jooq.Fields.CLEANUP_TASK_STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.COMPLETED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.JOB_ID
import com.ongo.infrastructure.persistence.jooq.Fields.LAST_ERROR_CODE
import com.ongo.infrastructure.persistence.jooq.Fields.OBJECT_KEY
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Tables.ACCOUNT_DELETION_OBJECT_TASKS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class AccountDeletionObjectTaskJooqRepository(
    private val dsl: DSLContext,
) : AccountDeletionObjectTaskRepository {

    /**
     * 중복은 조용히 건너뛴다. DB 삭제가 커밋되기 전에 죽으면 job 이 스냅샷부터 다시 도는데,
     * 그때 같은 키가 또 들어와도 삭제를 두 번 시도하지 않게 하는 멱등성의 근거다.
     * (job_id, object_key) 유니크 제약이 그 보증을 DB 에서 강제한다.
     */
    override fun saveAllIgnoringDuplicates(tasks: List<AccountDeletionObjectTask>): Int {
        if (tasks.isEmpty()) return 0
        var inserted = 0
        tasks.forEach { task ->
            inserted += dsl.insertInto(ACCOUNT_DELETION_OBJECT_TASKS)
                .columns(JOB_ID, OBJECT_KEY, CLEANUP_TASK_STATUS)
                .values(task.jobId, task.objectKey, task.status.name)
                .onConflictDoNothing()
                .execute()
        }
        return inserted
    }

    override fun findPending(jobId: Long, limit: Int): List<AccountDeletionObjectTask> =
        dsl.select()
            .from(ACCOUNT_DELETION_OBJECT_TASKS)
            .where(JOB_ID.eq(jobId))
            .and(CLEANUP_TASK_STATUS.eq(ObjectCleanupStatus.PENDING.name))
            .orderBy(CREATED_AT.asc(), ID.asc())
            .limit(limit)
            .fetch()
            .map { it.toTask() }

    override fun markDone(taskId: Long) {
        dsl.update(ACCOUNT_DELETION_OBJECT_TASKS)
            .set(CLEANUP_TASK_STATUS, ObjectCleanupStatus.DONE.name)
            .set(COMPLETED_AT, LocalDateTime.now())
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(taskId))
            .execute()
    }

    /** 실패는 PENDING 으로 남긴다. 상태를 바꾸지 않아야 다음 tick 이 다시 집는다. */
    override fun markAttemptFailed(taskId: Long, errorCode: String) {
        dsl.update(ACCOUNT_DELETION_OBJECT_TASKS)
            .set(CLEANUP_ATTEMPT_COUNT, CLEANUP_ATTEMPT_COUNT.plus(1))
            .set(LAST_ERROR_CODE, errorCode)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(taskId))
            .execute()
    }

    override fun markBlocked(taskId: Long, errorCode: String) {
        dsl.update(ACCOUNT_DELETION_OBJECT_TASKS)
            .set(CLEANUP_TASK_STATUS, ObjectCleanupStatus.BLOCKED.name)
            .set(LAST_ERROR_CODE, errorCode)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(taskId))
            .execute()
    }

    /** DONE 이 아닌 모든 건. PENDING 도 BLOCKED 도 "아직 실제로 안 지워졌다"는 뜻이다. */
    override fun countUnfinished(jobId: Long): Int =
        dsl.selectCount()
            .from(ACCOUNT_DELETION_OBJECT_TASKS)
            .where(JOB_ID.eq(jobId))
            .and(CLEANUP_TASK_STATUS.ne(ObjectCleanupStatus.DONE.name))
            .fetchOne(0, Int::class.java) ?: 0

    private fun Record.toTask() = AccountDeletionObjectTask(
        id = get(ID),
        jobId = get(JOB_ID),
        objectKey = get(OBJECT_KEY),
        status = runCatching { ObjectCleanupStatus.valueOf(get(CLEANUP_TASK_STATUS)) }
            .getOrDefault(ObjectCleanupStatus.PENDING),
        attemptCount = get(CLEANUP_ATTEMPT_COUNT) ?: 0,
        lastErrorCode = get(LAST_ERROR_CODE),
        createdAt = get(CREATED_AT, LocalDateTime::class.java),
        updatedAt = get(UPDATED_AT, LocalDateTime::class.java),
        completedAt = get(COMPLETED_AT, LocalDateTime::class.java),
    )
}
