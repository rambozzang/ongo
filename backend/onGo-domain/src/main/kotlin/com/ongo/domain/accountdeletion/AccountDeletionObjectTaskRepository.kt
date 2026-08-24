package com.ongo.domain.accountdeletion

/**
 * 탈퇴 객체 정리 원장.
 *
 * 스냅샷 기록은 **DB 삭제와 같은 트랜잭션**에서 일어나야 하고, 삭제 표시는 실제 객체를
 * 지운 뒤에 건별로 일어난다. 그래야 중간에 죽어도 어디까지 됐는지 원장이 알려준다.
 */
interface AccountDeletionObjectTaskRepository {
    /**
     * 스냅샷을 기록한다. 같은 (job, key) 가 이미 있으면 건너뛴다.
     *
     * 중복을 무시하는 이유는 재개 때문이다. DB 삭제가 커밋되기 전에 죽으면 job 은 다시
     * 스냅샷 단계부터 도는데, 그때 같은 키가 또 들어오면 삭제를 두 번 시도하게 된다.
     *
     * @return 실제로 삽입된 건수
     */
    fun saveAllIgnoringDuplicates(tasks: List<AccountDeletionObjectTask>): Int

    /** 아직 처리하지 못한 건. 오래된 것부터. */
    fun findPending(jobId: Long, limit: Int): List<AccountDeletionObjectTask>

    /** 실제로 지운 뒤에만 호출한다. */
    fun markDone(taskId: Long)

    /** 실패. PENDING 으로 남겨 다음 tick 이 다시 집게 한다. */
    fun markAttemptFailed(taskId: Long, errorCode: String)

    /** 사람이 봐야 하는 상태로 고정한다. 이 건이 있으면 job 은 완료되지 않는다. */
    fun markBlocked(taskId: Long, errorCode: String)

    /**
     * 아직 DONE 이 아닌 건수. 0 이어야만 job 을 COMPLETED 로 올릴 수 있다.
     *
     * PENDING 과 BLOCKED 를 함께 센다 — 둘 다 "아직 실제로 안 지워졌다"는 뜻이다.
     */
    fun countUnfinished(jobId: Long): Int
}
