package com.ongo.domain.ugc.shorts

interface PipelineRunRepository {
    fun save(run: PipelineRun): PipelineRun
    fun update(run: PipelineRun): PipelineRun
    fun findById(id: Long): PipelineRun?
    fun findByWorkspace(workspaceId: Long, offset: Int, limit: Int): List<PipelineRun>
    fun countByWorkspace(workspaceId: Long): Long
    fun delete(id: Long): Boolean
}
