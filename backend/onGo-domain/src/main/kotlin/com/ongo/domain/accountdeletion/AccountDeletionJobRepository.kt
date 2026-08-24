package com.ongo.domain.accountdeletion

/**
 * 계정 삭제 작업 저장소.
 *
 * 핵심은 [requestDeletion] 이다. 요청 row 생성과 사용자 게이트 전환이 **원자적**이어야 한다.
 * 나뉘면 "게이트는 켜졌는데 job 이 없다"거나 "job 은 있는데 쓰기가 계속 허용된다"는
 * 상태가 생기고, 둘 다 복구하기 어렵다.
 */
interface AccountDeletionJobRepository {

    /**
     * 다음 작업을 원자적으로 선점한다.
     *
     * `IN_PROGRESS` 가 오래된 작업은 워커가 죽은 것으로 보고 다시 선점할 수 있어야 한다.
     * 선점과 상태 변경은 하나의 트랜잭션이어야 여러 인스턴스가 같은 사용자를 지우지 않는다.
     */
    fun claimNext(now: java.time.LocalDateTime, staleBefore: java.time.LocalDateTime): AccountDeletionJob?

    /**
     * 삭제를 요청한다. 사용자 행을 잠그고 게이트를 켜면서 job 을 만든다. 한 트랜잭션이다.
     *
     * 이미 진행 중인 job 이 있으면 **새로 만들지 않고 그것을 돌려준다.**
     * 동시 요청 중 하나만 `REQUESTED` 를 만든다는 불변식은
     * `uq_account_deletion_jobs_active_user` 부분 유일 인덱스가 보증한다.
     *
     * @return 새로 만든 job 또는 이미 진행 중이던 job
     */
    fun requestDeletion(userId: Long, idempotencyKey: String): AccountDeletionJob

    /**
     * 정책으로 막힌 시도를 **종료 상태로** 기록한다. **게이트를 건드리지 않는다.**
     *
     * preflight 가 막았다는 것은 "지금은 지울 수 없다"는 뜻이지 "이 계정을 얼려라"가 아니다.
     * 여기서 게이트를 켜면 판단이 끝날 때까지 사용자가 아무것도 못 쓰는 **영구 동결**이 된다.
     * 정책 결정은 몇 주가 걸릴 수도 있는 일이라 그 사이 계정이 잠기면 안 된다.
     *
     * 그래서 재시도 가능한 진행 상태([requestDeletion])와 종료 상태를 구분한다.
     * - 진행 중 job = 게이트 켜짐. DB·외부 실패는 여기서 재시도한다
     * - 종료된 job(`BLOCKED_POLICY`) = 게이트 그대로 `ACTIVE`. 기록만 남는다
     */
    fun recordBlocked(
        userId: Long,
        idempotencyKey: String,
        errorCode: String,
        supportReference: String?,
    ): AccountDeletionJob

    /** 진행 중인 job. 없으면 null. */
    fun findActiveByUserId(userId: Long): AccountDeletionJob?

    /** 사용자의 가장 최근 삭제 요청. 삭제가 끝난 뒤에도 감사 상태를 조회할 수 있어야 한다. */
    fun findLatestByUserId(userId: Long): AccountDeletionJob?

    fun findByIdempotencyKey(key: String): AccountDeletionJob?

    fun findById(jobId: Long): AccountDeletionJob?

    /** 운영자가 확인할 수 있는 최근 삭제 작업 목록. */
    fun findRecent(limit: Int = 100): List<AccountDeletionJob>

    /** 실패/정책 차단 작업을 다시 요청 상태로 돌린다. */
    fun retry(jobId: Long): AccountDeletionJob?

    /** 정책 차단을 기록하고 사용자 쓰기 게이트를 다시 ACTIVE 로 돌린다. */
    fun markBlocked(jobId: Long, errorCode: String, supportReference: String?): AccountDeletionJob?

    /**
     * DB 삭제와 정리 원장이 커밋된 뒤의 상태.
     *
     * **이 시점은 완료가 아니다.** 외부 스토리지 객체가 아직 남아 있고, 그걸 지우는 것은
     * 커밋 이후에만 안전하다. 여기서 바로 COMPLETED 로 올리면 실제로는 남아 있는 파일을
     * "다 지웠다"고 기록하게 된다 — 개인정보 관점에서 가장 나쁜 거짓말이다.
     */
    fun markExternalCleanupPending(
        jobId: Long,
        unresolvedObjectRows: Int,
        dbCommittedAt: java.time.LocalDateTime = java.time.LocalDateTime.now(),
    ): AccountDeletionJob?

    /**
     * 외부 정리 실패 시 다음 시도 시각을 민다.
     *
     * 값이 없으면(NULL) 다음 tick 이 바로 집는다. 실패했을 때만 채워, 영구 실패하는 job 하나가
     * 매 tick 을 독차지해 다른 사용자의 탈퇴를 밀어내지 않게 한다.
     */
    fun scheduleCleanupRetry(jobId: Long, nextAttemptAt: java.time.LocalDateTime): AccountDeletionJob?

    /** 외부 객체까지 전부 지워진 뒤에만 호출한다. */
    fun markCompleted(jobId: Long, completedAt: java.time.LocalDateTime = java.time.LocalDateTime.now()): AccountDeletionJob?

    /** DB 삭제가 롤백된 뒤 재요청 가능 상태로 돌린다. */
    fun markFailed(jobId: Long, errorCode: String, supportReference: String?): AccountDeletionJob?

    /** 사용자의 현재 게이트 상태. */
    fun findDeletionState(userId: Long): AccountDeletionState?
}
