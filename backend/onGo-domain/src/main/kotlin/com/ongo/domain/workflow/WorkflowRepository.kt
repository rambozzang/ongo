package com.ongo.domain.workflow

interface WorkflowRepository {
    fun findById(id: Long): VisualWorkflow?
    fun findByUserId(userId: Long): List<VisualWorkflow>
    fun save(workflow: VisualWorkflow): VisualWorkflow
    fun update(workflow: VisualWorkflow): VisualWorkflow
    fun delete(id: Long)
}
