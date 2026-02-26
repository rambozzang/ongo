package com.ongo.domain.contentrepurposer

interface RepurposeJobRepository {
    fun findByWorkspaceId(workspaceId: Long): List<RepurposeJob>
    fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<RepurposeJob>
    fun save(job: RepurposeJob): RepurposeJob
}
