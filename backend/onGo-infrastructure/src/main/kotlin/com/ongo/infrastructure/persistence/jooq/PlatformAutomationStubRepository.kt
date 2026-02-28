package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.platformautomation.AutomationLog
import com.ongo.domain.platformautomation.AutomationLogRepository
import com.ongo.domain.platformautomation.AutomationRule
import com.ongo.domain.platformautomation.AutomationRuleRepository as PlatformAutomationRuleRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class PlatformAutomationRuleJooqRepository(
    private val dsl: DSLContext,
) : PlatformAutomationRuleRepository {

    companion object {
        private val TABLE = DSL.table("automation_rules")
        private val NAME = DSL.field("name", String::class.java)
        private val PLATFORM = DSL.field("platform", String::class.java)
        private val TRIGGER_TYPE = DSL.field("trigger_type", String::class.java)
        private val ACTION_TYPE = DSL.field("action_type", String::class.java)
        private val CONDITION_TEXT = DSL.field("condition_text", String::class.java)
        private val IS_ACTIVE = DSL.field("is_active", Boolean::class.java)
        private val EXECUTION_COUNT = DSL.field("execution_count", Int::class.java)
        private val LAST_EXECUTED = DSL.field("last_executed", LocalDateTime::class.java)
    }

    override fun findById(id: Long): AutomationRule? =
        dsl.select().from(TABLE)
            .where(ID.eq(id))
            .fetchOne()?.toRule()

    override fun findByUserId(userId: Long): List<AutomationRule> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .fetch { it.toRule() }

    override fun save(rule: AutomationRule): AutomationRule {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, rule.userId)
            .set(NAME, rule.name)
            .set(PLATFORM, rule.platform)
            .set(TRIGGER_TYPE, rule.triggerType)
            .set(ACTION_TYPE, rule.actionType)
            .set(CONDITION_TEXT, rule.conditionText)
            .set(IS_ACTIVE, rule.isActive)
            .set(EXECUTION_COUNT, rule.executionCount)
            .set(LAST_EXECUTED, rule.lastExecuted)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    override fun update(rule: AutomationRule): AutomationRule {
        dsl.update(TABLE)
            .set(NAME, rule.name)
            .set(PLATFORM, rule.platform)
            .set(TRIGGER_TYPE, rule.triggerType)
            .set(ACTION_TYPE, rule.actionType)
            .set(CONDITION_TEXT, rule.conditionText)
            .set(IS_ACTIVE, rule.isActive)
            .set(EXECUTION_COUNT, rule.executionCount)
            .set(LAST_EXECUTED, rule.lastExecuted)
            .where(ID.eq(rule.id))
            .execute()

        return findById(rule.id!!)!!
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toRule(): AutomationRule =
        AutomationRule(
            id = get(ID),
            userId = get(USER_ID),
            name = get(NAME),
            platform = get(PLATFORM),
            triggerType = get(TRIGGER_TYPE),
            actionType = get(ACTION_TYPE),
            conditionText = get(CONDITION_TEXT),
            isActive = get(IS_ACTIVE) ?: true,
            executionCount = get(EXECUTION_COUNT) ?: 0,
            lastExecuted = localDateTime(LAST_EXECUTED),
            createdAt = localDateTime(CREATED_AT),
        )
}

@Repository
class AutomationLogJooqRepository(
    private val dsl: DSLContext,
) : AutomationLogRepository {

    companion object {
        private val TABLE = DSL.table("automation_logs")
        private val RULE_TABLE = DSL.table("automation_rules")
        private val RULE_ID = DSL.field("rule_id", Long::class.java)
        private val RULE_NAME = DSL.field("rule_name", String::class.java)
        private val MESSAGE = DSL.field("message", String::class.java)
        private val EXECUTED_AT = DSL.field("executed_at", LocalDateTime::class.java)
    }

    override fun findByRuleId(ruleId: Long): List<AutomationLog> =
        dsl.select().from(TABLE)
            .where(RULE_ID.eq(ruleId))
            .orderBy(EXECUTED_AT.desc())
            .fetch { it.toLog() }

    override fun findByUserId(userId: Long): List<AutomationLog> =
        dsl.select(
            DSL.field("automation_logs.id", Long::class.java),
            RULE_ID,
            RULE_NAME,
            DSL.field("automation_logs.status", String::class.java),
            MESSAGE,
            EXECUTED_AT,
        )
            .from(TABLE)
            .join(RULE_TABLE).on(RULE_ID.eq(DSL.field("automation_rules.id", Long::class.java)))
            .where(DSL.field("automation_rules.user_id", Long::class.java).eq(userId))
            .orderBy(EXECUTED_AT.desc())
            .fetch { it.toLog() }

    override fun save(log: AutomationLog): AutomationLog {
        val id = dsl.insertInto(TABLE)
            .set(RULE_ID, log.ruleId)
            .set(RULE_NAME, log.ruleName)
            .set(STATUS, log.status)
            .set(MESSAGE, log.message)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()!!.toLog()
    }

    private fun Record.toLog(): AutomationLog =
        AutomationLog(
            id = get(ID),
            ruleId = get(RULE_ID),
            ruleName = get(RULE_NAME),
            status = get(STATUS) ?: "SUCCESS",
            message = get(MESSAGE),
            executedAt = localDateTime(EXECUTED_AT),
        )
}
