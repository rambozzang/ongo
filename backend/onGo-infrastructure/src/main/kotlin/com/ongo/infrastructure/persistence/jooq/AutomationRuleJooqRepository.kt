package com.ongo.infrastructure.persistence.jooq

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.ongo.domain.automation.AutomationRule
import com.ongo.domain.automation.AutomationRuleRepository
import com.ongo.infrastructure.persistence.jooq.Fields.ACTION_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DESCRIPTION
import com.ongo.infrastructure.persistence.jooq.Fields.EXECUTION_COUNT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.IS_ACTIVE
import com.ongo.infrastructure.persistence.jooq.Fields.LAST_TRIGGERED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.NAME
import com.ongo.infrastructure.persistence.jooq.Fields.TRIGGER_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.AUTOMATION_RULES
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class AutomationRuleJooqRepository(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
) : AutomationRuleRepository {

    companion object {
        private val TRIGGER_CONFIG_JSONB = DSL.field("trigger_config", JSONB::class.java)
        private val ACTION_CONFIG_JSONB = DSL.field("action_config", JSONB::class.java)
    }

    override fun findById(id: Long): AutomationRule? =
        dsl.select()
            .from(AUTOMATION_RULES)
            .where(ID.eq(id))
            .fetchOne()
            ?.toAutomationRule()

    override fun findByUserId(userId: Long): List<AutomationRule> =
        dsl.select()
            .from(AUTOMATION_RULES)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toAutomationRule() }

    override fun findAll(): List<AutomationRule> =
        dsl.select()
            .from(AUTOMATION_RULES)
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toAutomationRule() }

    override fun save(rule: AutomationRule): AutomationRule {
        val triggerJson = JSONB.jsonb(objectMapper.writeValueAsString(rule.triggerConfig))
        val actionJson = JSONB.jsonb(objectMapper.writeValueAsString(rule.actionConfig))

        val id = dsl.insertInto(AUTOMATION_RULES)
            .set(USER_ID, rule.userId)
            .set(NAME, rule.name)
            .set(DESCRIPTION, rule.description)
            .set(TRIGGER_TYPE, rule.triggerType)
            .set(TRIGGER_CONFIG_JSONB, triggerJson)
            .set(ACTION_TYPE, rule.actionType)
            .set(ACTION_CONFIG_JSONB, actionJson)
            .set(IS_ACTIVE, rule.isActive)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(rule: AutomationRule): AutomationRule {
        val triggerJson = JSONB.jsonb(objectMapper.writeValueAsString(rule.triggerConfig))
        val actionJson = JSONB.jsonb(objectMapper.writeValueAsString(rule.actionConfig))

        dsl.update(AUTOMATION_RULES)
            .set(NAME, rule.name)
            .set(DESCRIPTION, rule.description)
            .set(TRIGGER_TYPE, rule.triggerType)
            .set(TRIGGER_CONFIG_JSONB, triggerJson)
            .set(ACTION_TYPE, rule.actionType)
            .set(ACTION_CONFIG_JSONB, actionJson)
            .set(IS_ACTIVE, rule.isActive)
            .set(EXECUTION_COUNT, rule.executionCount)
            .set(LAST_TRIGGERED_AT, rule.lastTriggeredAt)
            .where(ID.eq(rule.id))
            .execute()

        return findById(rule.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(AUTOMATION_RULES)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toAutomationRule(): AutomationRule {
        val triggerRaw = get("trigger_config")
        val triggerConfig: Map<String, Any?> = try {
            when (triggerRaw) {
                is JSONB -> objectMapper.readValue(triggerRaw.data())
                is String -> objectMapper.readValue(triggerRaw)
                else -> emptyMap()
            }
        } catch (_: Exception) { emptyMap() }

        val actionRaw = get("action_config")
        val actionConfig: Map<String, Any?> = try {
            when (actionRaw) {
                is JSONB -> objectMapper.readValue(actionRaw.data())
                is String -> objectMapper.readValue(actionRaw)
                else -> emptyMap()
            }
        } catch (_: Exception) { emptyMap() }

        return AutomationRule(
            id = get(ID),
            userId = get(USER_ID),
            name = get(NAME),
            description = get(DESCRIPTION),
            triggerType = get(TRIGGER_TYPE),
            triggerConfig = triggerConfig,
            actionType = get(ACTION_TYPE),
            actionConfig = actionConfig,
            isActive = get(IS_ACTIVE) ?: false,
            lastTriggeredAt = localDateTime(LAST_TRIGGERED_AT),
            executionCount = get(EXECUTION_COUNT) ?: 0,
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
    }
}
