package com.ongo.domain.ugc.shorts

interface RunStageRepository {
    fun save(stage: RunStage): RunStage
    fun update(stage: RunStage): RunStage
    fun findByRunId(runId: Long): List<RunStage>
    fun findByRunIdAndStage(runId: Long, stage: PipelineStage): RunStage?

    /** 재실행 시 [fromSortOrder] 이상 단계 기록을 지운다. 삭제 건수를 반환한다. */
    fun deleteFrom(runId: Long, fromSortOrder: Int): Int
}
