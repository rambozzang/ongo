package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.workflow.*
import org.springframework.stereotype.Repository

@Repository
class WorkflowStubRepository : WorkflowRepository {
    override fun findById(id: Long): VisualWorkflow? = null
    override fun findByUserId(userId: Long): List<VisualWorkflow> = emptyList()
    override fun save(workflow: VisualWorkflow): VisualWorkflow = workflow.copy(id = 1)
    override fun update(workflow: VisualWorkflow): VisualWorkflow = workflow
    override fun delete(id: Long) {}
}
