package com.ongo.domain.ugc.shorts

interface PipelineRunRepository {
    data class SaveResult(val run: PipelineRun, val created: Boolean)

    fun save(run: PipelineRun): PipelineRun

    /** 멱등 키가 있으면 동시 요청 중 하나만 새 실행을 만든다. */
    fun saveIdempotently(run: PipelineRun): SaveResult = SaveResult(save(run), created = true)

    fun findByUserIdAndIdempotencyKey(userId: Long, idempotencyKey: String): PipelineRun? = null

    fun update(run: PipelineRun): PipelineRun
    fun findById(id: Long): PipelineRun?
    fun findByStatus(status: PipelineRunStatus, limit: Int): List<PipelineRun>
    fun findByWorkspace(workspaceId: Long, offset: Int, limit: Int): List<PipelineRun>
    fun countByWorkspace(workspaceId: Long): Long
    fun delete(id: Long): Boolean
}
