package com.ongo.domain.ugc.shorts

interface RunStageRepository {
    fun save(stage: RunStage): RunStage
    fun update(stage: RunStage): RunStage
    fun findByRunId(runId: Long): List<RunStage>
    fun findByRunIdAndStage(runId: Long, stage: PipelineStage): RunStage?

    /** 재실행 시 [fromSortOrder] 이상 단계 기록을 지운다. 삭제 건수를 반환한다. */
    fun deleteFrom(runId: Long, fromSortOrder: Int): Int

    /**
     * 차감됐지만 아직 정산되지 않은 단계. `RUNNING` 이고 `refunded_credits = 0` 이며
     * 청구액이 있는 행이다.
     *
     * **완료된 단계는 절대 포함되지 않는다.** 그 단계는 실제로 일한 대가로 정당하게
     * 청구된 것이라 환불 대상이 아니다.
     *
     * @param fromSortOrder 이 순서 이상만. 재실행·삭제가 지울 범위와 같은 조건을 쓴다.
     */
    fun findUnsettled(runId: Long, fromSortOrder: Int = 0): List<RunStage>

    /**
     * 정산 표식을 **조건부로** 세운다. 세웠으면 true.
     *
     * `WHERE status = 'RUNNING' AND refunded_credits = 0` 이므로 동시에 들어온 두 정산 중
     * 하나만 이긴다. 읽고-판단하고-쓰면 둘 다 통과해 두 번 환불된다.
     *
     * 단계를 `FAILED` 로 함께 닫는다. 닫지 않으면 [findUnsettled] 가 같은 행을 영원히 다시
     * 집는다. 호출자는 **이 메서드가 true 를 돌려준 경우에만** 실제 환불을 수행하며, 둘은
     * 같은 트랜잭션 안에 있어야 한다 — 환불이 실패하면 표식도 함께 롤백되어 다음 시도가
     * 같은 행을 다시 집을 수 있다.
     */
    fun settleRefund(stageId: Long, refundedCredits: Int, reason: String): Boolean
}
