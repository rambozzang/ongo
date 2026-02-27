package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentrepurposer.*
import org.springframework.stereotype.Repository

@Repository
class RepurposeJobStubRepository : RepurposeJobRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<RepurposeJob> = emptyList()
    override fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<RepurposeJob> = emptyList()
    override fun save(job: RepurposeJob): RepurposeJob = job.copy(id = 1)
}

@Repository
class RepurposeTemplateStubRepository : RepurposeTemplateRepository {
    override fun findByWorkspaceIdOrDefault(workspaceId: Long): List<RepurposeTemplate> = emptyList()
}
