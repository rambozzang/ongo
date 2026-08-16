package com.ongo.domain.accountdeletion

/**
 * 정책으로 승인된 사용자 소유 데이터만 삭제하는 포트.
 *
 * 구현체는 삭제와 job 완료 기록을 같은 DB 트랜잭션으로 묶어야 한다. 그래야 프로세스가
 * 중간에 죽어도 사용자 데이터만 지워지고 job 이 남는 상태를 만들지 않는다.
 */
interface AccountDeletionDataPort {
    fun deleteUserDataAndComplete(
        jobId: Long,
        userId: Long,
        policies: List<UserFkPolicy>,
    )
}
