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

    /** 진행 중인 job. 없으면 null. */
    fun findActiveByUserId(userId: Long): AccountDeletionJob?

    fun findByIdempotencyKey(key: String): AccountDeletionJob?

    /** 사용자의 현재 게이트 상태. */
    fun findDeletionState(userId: Long): AccountDeletionState?
}
