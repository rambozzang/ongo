package com.ongo.domain.accountdeletion

/**
 * 정책으로 승인된 사용자 소유 데이터만 삭제하는 포트.
 *
 * 구현체는 삭제와 job 완료 기록을 같은 DB 트랜잭션으로 묶어야 한다. 그래야 프로세스가
 * 중간에 죽어도 사용자 데이터만 지워지고 job 이 남는 상태를 만들지 않는다.
 */
interface AccountDeletionDataPort {
    /**
     * 지울 객체 키를 원장에 적고 사용자 DB 데이터를 지운다. **job 을 완료시키지 않는다.**
     *
     * 완료는 외부 객체까지 실제로 지운 뒤의 일이다. 여기서 COMPLETED 로 올리면 버킷에는
     * 남아 있는데 "다 지웠다"고 기록하게 된다.
     *
     * 순서가 중요하다. 원장 기록과 DB 삭제가 **같은 트랜잭션**이라, 커밋됐다는 사실이 곧
     * "삭제가 확정됐고 지울 키 목록도 남았다"는 뜻이 된다. 반대로 객체를 먼저 지우면
     * 그 트랜잭션이 롤백됐을 때 살아있는 계정의 파일을 잃는다 — 되돌릴 수 없다.
     *
     * @return 원장에 적힌 스냅샷 결과. 호출자가 완료 가능 여부를 판단하는 근거다.
     */
    fun snapshotObjectsAndDeleteUserData(
        jobId: Long,
        userId: Long,
        policies: List<UserFkPolicy>,
    ): UserObjectSnapshot
}
