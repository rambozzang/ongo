package com.ongo.infrastructure.persistence.jooq

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.ongo.domain.automation.*
import com.ongo.infrastructure.persistence.jooq.Fields.ACTION_RESULTS
import com.ongo.infrastructure.persistence.jooq.Fields.ACTION_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.COMPLETED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.CONFIG_JSONB
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DELAY_MINUTES
import com.ongo.infrastructure.persistence.jooq.Fields.DESCRIPTION
import com.ongo.infrastructure.persistence.jooq.Fields.ENABLED
import com.ongo.infrastructure.persistence.jooq.Fields.ERROR_MESSAGE
import com.ongo.infrastructure.persistence.jooq.Fields.EXECUTION_COUNT
import com.ongo.infrastructure.persistence.jooq.Fields.EXPRESSION
import com.ongo.infrastructure.persistence.jooq.Fields.FIELD
import com.ongo.infrastructure.persistence.jooq.Fields.GROUP_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.ID
// LAST_EXECUTED_AT is defined as LAST_EXECUTED_AT_FIELD (private val) to avoid name collision
import com.ongo.infrastructure.persistence.jooq.Fields.NAME
import com.ongo.infrastructure.persistence.jooq.Fields.OPERATOR
import com.ongo.infrastructure.persistence.jooq.Fields.PARENT_CONDITION_ID
import com.ongo.infrastructure.persistence.jooq.Fields.SORT_ORDER
import com.ongo.infrastructure.persistence.jooq.Fields.STARTED_AT_EXEC
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.TRIGGER_DATA
import com.ongo.infrastructure.persistence.jooq.Fields.TRIGGER_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VALUE
import com.ongo.infrastructure.persistence.jooq.Fields.WORKFLOW_ID
import com.ongo.infrastructure.persistence.jooq.Tables.AUTOMATION_WORKFLOWS
import com.ongo.infrastructure.persistence.jooq.Tables.WORKFLOW_ACTIONS
import com.ongo.infrastructure.persistence.jooq.Tables.WORKFLOW_CONDITIONS
import com.ongo.infrastructure.persistence.jooq.Tables.WORKFLOW_EXECUTIONS
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

private val LAST_EXECUTED_AT_FIELD = DSL.field("last_executed_at", java.time.LocalDateTime::class.java)

@Repository
class AutomationWorkflowJooqRepository(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
) : AutomationWorkflowRepository {

    companion object {
        private val TRIGGER_CONFIG_JSONB = DSL.field("trigger_config", JSONB::class.java)
        private val CONFIG_FIELD_JSONB = DSL.field("config", JSONB::class.java)
        private val TRIGGER_DATA_JSONB = DSL.field("trigger_data", JSONB::class.java)
        private val ACTION_RESULTS_JSONB = DSL.field("action_results", JSONB::class.java)
    }

    // ─── Workflow CRUD ───

    override fun findById(id: Long): AutomationWorkflow? {
        val record = dsl.select()
            .from(AUTOMATION_WORKFLOWS)
            .where(ID.eq(id))
            .fetchOne() ?: return null

        val workflow = record.toWorkflow()
        val conditions = findConditionsByWorkflowId(id)
        val actions = findActionsByWorkflowId(id)
        return workflow.copy(conditions = conditions, actions = actions)
    }

    override fun findByUserId(userId: Long): List<AutomationWorkflow> {
        val workflows = dsl.select()
            .from(AUTOMATION_WORKFLOWS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toWorkflow() }

        if (workflows.isEmpty()) return emptyList()

        val workflowIds = workflows.mapNotNull { it.id }

        // Batch fetch all conditions and actions (eliminates 2N+1 → 3 queries)
        val allConditions = dsl.select()
            .from(WORKFLOW_CONDITIONS)
            .where(WORKFLOW_ID.`in`(workflowIds))
            .orderBy(SORT_ORDER)
            .fetch()
            .map { it.toCondition() }
        val conditionsByWorkflow = allConditions.groupBy { it.workflowId }

        val allActions = dsl.select()
            .from(WORKFLOW_ACTIONS)
            .where(WORKFLOW_ID.`in`(workflowIds))
            .orderBy(SORT_ORDER)
            .fetch()
            .map { it.toAction() }
        val actionsByWorkflow = allActions.groupBy { it.workflowId }

        return workflows.map { w ->
            val wId = w.id!!
            val conditions = buildConditionTree(conditionsByWorkflow[wId] ?: emptyList())
            val actions = actionsByWorkflow[wId] ?: emptyList()
            w.copy(conditions = conditions, actions = actions)
        }
    }

    override fun save(workflow: AutomationWorkflow): AutomationWorkflow {
        val triggerJson = JSONB.jsonb(objectMapper.writeValueAsString(workflow.triggerConfig))

        val record = dsl.insertInto(AUTOMATION_WORKFLOWS)
            .set(USER_ID, workflow.userId)
            .set(NAME, workflow.name)
            .set(DESCRIPTION, workflow.description)
            .set(TRIGGER_TYPE, workflow.triggerType)
            .set(TRIGGER_CONFIG_JSONB, triggerJson)
            .set(ENABLED, workflow.enabled)
            .returning()
            .fetchOne()!!

        val saved = record.toWorkflow()
        val savedConditions = if (workflow.conditions.isNotEmpty()) {
            saveConditions(saved.id!!, workflow.conditions)
        } else emptyList()
        val savedActions = if (workflow.actions.isNotEmpty()) {
            saveActions(saved.id!!, workflow.actions)
        } else emptyList()

        return saved.copy(conditions = savedConditions, actions = savedActions)
    }

    override fun update(workflow: AutomationWorkflow): AutomationWorkflow {
        val triggerJson = JSONB.jsonb(objectMapper.writeValueAsString(workflow.triggerConfig))

        val record = dsl.update(AUTOMATION_WORKFLOWS)
            .set(NAME, workflow.name)
            .set(DESCRIPTION, workflow.description)
            .set(TRIGGER_TYPE, workflow.triggerType)
            .set(TRIGGER_CONFIG_JSONB, triggerJson)
            .set(ENABLED, workflow.enabled)
            .set(EXECUTION_COUNT, workflow.executionCount)
            .set(LAST_EXECUTED_AT_FIELD, workflow.lastExecutedAt)
            .where(ID.eq(workflow.id))
            .returning()
            .fetchOne()!!

        // Replace conditions and actions
        val workflowId = workflow.id!!
        deleteConditionsByWorkflowId(workflowId)
        deleteActionsByWorkflowId(workflowId)

        val savedConditions = if (workflow.conditions.isNotEmpty()) {
            saveConditions(workflowId, workflow.conditions)
        } else emptyList()
        val savedActions = if (workflow.actions.isNotEmpty()) {
            saveActions(workflowId, workflow.actions)
        } else emptyList()

        return record.toWorkflow().copy(conditions = savedConditions, actions = savedActions)
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(AUTOMATION_WORKFLOWS)
            .where(ID.eq(id))
            .execute()
    }

    // ─── Conditions ───

    override fun saveConditions(workflowId: Long, conditions: List<WorkflowCondition>): List<WorkflowCondition> {
        return conditions.map { saveCondition(workflowId, it, null) }
    }

    private fun saveCondition(workflowId: Long, condition: WorkflowCondition, parentId: Long?): WorkflowCondition {
        val record = dsl.insertInto(WORKFLOW_CONDITIONS)
            .set(WORKFLOW_ID, workflowId)
            .set(PARENT_CONDITION_ID, parentId)
            .set(GROUP_TYPE, condition.groupType.name)
            .set(FIELD, condition.field)
            .set(OPERATOR, condition.operator?.name)
            .set(VALUE, condition.value)
            .set(EXPRESSION, condition.expression)
            .set(SORT_ORDER, condition.sortOrder)
            .returning()
            .fetchOne()!!

        val savedId = record.get(ID)
        val nestedSaved = condition.nestedConditions.map { saveCondition(workflowId, it, savedId) }

        return condition.copy(
            id = savedId,
            workflowId = workflowId,
            parentConditionId = parentId,
            nestedConditions = nestedSaved,
        )
    }

    override fun findConditionsByWorkflowId(workflowId: Long): List<WorkflowCondition> {
        val allConditions = dsl.select()
            .from(WORKFLOW_CONDITIONS)
            .where(WORKFLOW_ID.eq(workflowId))
            .orderBy(SORT_ORDER)
            .fetch()
            .map { it.toCondition() }

        // Build tree: only return root-level conditions (parentConditionId is null)
        return buildConditionTree(allConditions)
    }

    private fun buildConditionTree(all: List<WorkflowCondition>): List<WorkflowCondition> {
        val byParent = all.groupBy { it.parentConditionId }
        fun attachChildren(condition: WorkflowCondition): WorkflowCondition {
            val children = byParent[condition.id]?.map { attachChildren(it) } ?: emptyList()
            return condition.copy(nestedConditions = children)
        }
        return (byParent[null] ?: emptyList()).map { attachChildren(it) }
    }

    override fun deleteConditionsByWorkflowId(workflowId: Long) {
        dsl.deleteFrom(WORKFLOW_CONDITIONS)
            .where(WORKFLOW_ID.eq(workflowId))
            .execute()
    }

    // ─── Actions ───

    override fun saveActions(workflowId: Long, actions: List<WorkflowAction>): List<WorkflowAction> {
        return actions.map { action ->
            val configJson = JSONB.jsonb(objectMapper.writeValueAsString(action.config))
            val record = dsl.insertInto(WORKFLOW_ACTIONS)
                .set(WORKFLOW_ID, workflowId)
                .set(ACTION_TYPE, action.actionType)
                .set(CONFIG_FIELD_JSONB, configJson)
                .set(DSL.field("delay_minutes", Int::class.java), action.delayMinutes)
                .set(SORT_ORDER, action.sortOrder)
                .returning()
                .fetchOne()!!

            action.copy(id = record.get(ID), workflowId = workflowId)
        }
    }

    override fun findActionsByWorkflowId(workflowId: Long): List<WorkflowAction> {
        return dsl.select()
            .from(WORKFLOW_ACTIONS)
            .where(WORKFLOW_ID.eq(workflowId))
            .orderBy(SORT_ORDER)
            .fetch()
            .map { it.toAction() }
    }

    override fun deleteActionsByWorkflowId(workflowId: Long) {
        dsl.deleteFrom(WORKFLOW_ACTIONS)
            .where(WORKFLOW_ID.eq(workflowId))
            .execute()
    }

    // ─── Executions ───

    override fun saveExecution(execution: WorkflowExecution): WorkflowExecution {
        val triggerJson = JSONB.jsonb(objectMapper.writeValueAsString(execution.triggerData))
        val resultsJson = JSONB.jsonb(objectMapper.writeValueAsString(execution.actionResults))

        val record = dsl.insertInto(WORKFLOW_EXECUTIONS)
            .set(WORKFLOW_ID, execution.workflowId)
            .set(USER_ID, execution.userId)
            .set(TRIGGER_DATA_JSONB, triggerJson)
            .set(STATUS, execution.status.name)
            .set(ACTION_RESULTS_JSONB, resultsJson)
            .set(DSL.field("error_message", String::class.java), execution.errorMessage)
            .set(COMPLETED_AT, execution.completedAt)
            .returning()
            .fetchOne()!!

        return execution.copy(id = record.get(ID))
    }

    override fun findExecutionsByWorkflowId(workflowId: Long, limit: Int): List<WorkflowExecution> {
        return dsl.select()
            .from(WORKFLOW_EXECUTIONS)
            .where(WORKFLOW_ID.eq(workflowId))
            .orderBy(DSL.field("started_at", java.time.LocalDateTime::class.java).desc())
            .limit(limit)
            .fetch()
            .map { it.toExecution() }
    }

    // ─── Mappers ───

    private fun Record.toWorkflow(): AutomationWorkflow {
        val triggerRaw = get("trigger_config")
        val triggerConfig: Map<String, Any?> = when (triggerRaw) {
            is JSONB -> objectMapper.readValue(triggerRaw.data())
            is String -> objectMapper.readValue(triggerRaw)
            else -> emptyMap()
        }

        return AutomationWorkflow(
            id = get(ID),
            userId = get(USER_ID),
            name = get(NAME),
            description = get(DESCRIPTION),
            triggerType = get(TRIGGER_TYPE),
            triggerConfig = triggerConfig,
            enabled = get(ENABLED) ?: false,
            executionCount = get(EXECUTION_COUNT) ?: 0,
            lastExecutedAt = localDateTime(LAST_EXECUTED_AT_FIELD),
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
    }

    private fun Record.toCondition(): WorkflowCondition {
        val operatorStr = get(OPERATOR)
        return WorkflowCondition(
            id = get(ID),
            workflowId = get(WORKFLOW_ID),
            groupType = try { ConditionGroupType.valueOf(get(GROUP_TYPE) ?: "AND") } catch (_: Exception) { ConditionGroupType.AND },
            field = get(FIELD),
            operator = operatorStr?.let { try { ConditionOperator.valueOf(it) } catch (_: Exception) { null } },
            value = get(VALUE),
            expression = get(EXPRESSION),
            parentConditionId = get(PARENT_CONDITION_ID),
        )
    }

    private val SORT_ORDER_FIELD = DSL.field("sort_order", Int::class.java)

    private fun Record.toAction(): WorkflowAction {
        val configRaw = get("config")
        val config: Map<String, Any?> = when (configRaw) {
            is JSONB -> objectMapper.readValue(configRaw.data())
            is String -> objectMapper.readValue(configRaw)
            else -> emptyMap()
        }

        return WorkflowAction(
            id = get(ID),
            workflowId = get(WORKFLOW_ID),
            actionType = get(ACTION_TYPE),
            config = config,
            delayMinutes = get(DSL.field("delay_minutes", Int::class.java)) ?: 0,
            sortOrder = get(SORT_ORDER_FIELD) ?: 0,
        )
    }

    private fun Record.toExecution(): WorkflowExecution {
        val triggerRaw = get("trigger_data")
        val triggerData: Map<String, Any?> = when (triggerRaw) {
            is JSONB -> objectMapper.readValue(triggerRaw.data())
            is String -> objectMapper.readValue(triggerRaw)
            else -> emptyMap()
        }

        val resultsRaw = get("action_results")
        val actionResults: List<ActionExecutionResult> = when (resultsRaw) {
            is JSONB -> objectMapper.readValue(resultsRaw.data())
            is String -> objectMapper.readValue(resultsRaw)
            else -> emptyList()
        }

        return WorkflowExecution(
            id = get(ID),
            workflowId = get(WORKFLOW_ID),
            userId = get(USER_ID),
            triggerData = triggerData,
            status = try { WorkflowExecutionStatus.valueOf(get(STATUS) ?: "RUNNING") } catch (_: Exception) { WorkflowExecutionStatus.RUNNING },
            actionResults = actionResults,
            errorMessage = get(DSL.field("error_message", String::class.java)),
            startedAt = localDateTime(STARTED_AT_EXEC) ?: java.time.LocalDateTime.now(),
            completedAt = localDateTime(COMPLETED_AT),
        )
    }
}
