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

    fun findByIdempotencyKey(key: String): AccountDeletionJob?

    /** 사용자의 현재 게이트 상태. */
    fun findDeletionState(userId: Long): AccountDeletionState?
}
