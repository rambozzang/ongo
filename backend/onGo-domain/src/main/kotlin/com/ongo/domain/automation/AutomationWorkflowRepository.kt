package com.ongo.domain.automation

interface AutomationWorkflowRepository {
    fun findById(id: Long): AutomationWorkflow?
    fun findByUserId(userId: Long): List<AutomationWorkflow>
    fun save(workflow: AutomationWorkflow): AutomationWorkflow
    fun update(workflow: AutomationWorkflow): AutomationWorkflow
    fun delete(id: Long)

    fun saveConditions(workflowId: Long, conditions: List<WorkflowCondition>): List<WorkflowCondition>
    fun findConditionsByWorkflowId(workflowId: Long): List<WorkflowCondition>
    fun deleteConditionsByWorkflowId(workflowId: Long)

    fun saveActions(workflowId: Long, actions: List<WorkflowAction>): List<WorkflowAction>
    fun findActionsByWorkflowId(workflowId: Long): List<WorkflowAction>
    fun deleteActionsByWorkflowId(workflowId: Long)

    fun saveExecution(execution: WorkflowExecution): WorkflowExecution
    fun findExecutionsByWorkflowId(workflowId: Long, limit: Int = 50): List<WorkflowExecution>
}
