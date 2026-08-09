package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.platformautomation.AutomationLog
import com.ongo.domain.platformautomation.AutomationLogRepository
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

/**
 * Reads the legacy automation_logs table through the current automation_rules
 * ownership boundary. A log id by itself is not sufficient for tenant safety,
 * so every list query is constrained by the owning rule's user_id.
 */
@Repository
class AutomationLogJooqRepository(
    private val dsl: DSLContext,
) : AutomationLogRepository {
    private val logs = DSL.table("automation_logs")
    private val rules = DSL.table("automation_rules")

    private val logId = DSL.field(DSL.name("automation_logs", "id"), Long::class.java)
    private val logRuleId = DSL.field(DSL.name("automation_logs", "rule_id"), Long::class.java)
    private val logRuleName = DSL.field(DSL.name("automation_logs", "rule_name"), String::class.java)
    private val logStatus = DSL.field(DSL.name("automation_logs", "status"), String::class.java)
    private val logMessage = DSL.field(DSL.name("automation_logs", "message"), String::class.java)
    private val logExecutedAt = DSL.field(DSL.name("automation_logs", "executed_at"), java.time.LocalDateTime::class.java)
    private val ruleId = DSL.field(DSL.name("automation_rules", "id"), Long::class.java)
    private val ruleUserId = DSL.field(DSL.name("automation_rules", "user_id"), Long::class.java)

    override fun findByRuleId(ruleId: Long): List<AutomationLog> =
        dsl.select(logId, logRuleId, logRuleName, logStatus, logMessage, logExecutedAt)
            .from(logs)
            .where(logRuleId.eq(ruleId))
            .orderBy(logExecutedAt.desc())
            .fetch()
            .map { it.toAutomationLog() }

    override fun findByUserId(userId: Long): List<AutomationLog> =
        dsl.select(logId, logRuleId, logRuleName, logStatus, logMessage, logExecutedAt)
            .from(logs)
            .where(logRuleId.`in`(
                dsl.select(ruleId).from(rules).where(ruleUserId.eq(userId)),
            ))
            .orderBy(logExecutedAt.desc())
            .limit(200)
            .fetch()
            .map { it.toAutomationLog() }

    override fun save(log: AutomationLog): AutomationLog {
        val id = dsl.insertInto(logs)
            .set(DSL.field("rule_id", Long::class.java), log.ruleId)
            .set(DSL.field("rule_name", String::class.java), log.ruleName)
            .set(DSL.field("status", String::class.java), log.status)
            .set(DSL.field("message", String::class.java), log.message)
            .set(DSL.field("executed_at", java.time.LocalDateTime::class.java), log.executedAt)
            .returningResult(DSL.field("id", Long::class.java))
            .fetchOne()!!
            .get(DSL.field("id", Long::class.java))
        return log.copy(id = id)
    }

    private fun Record.toAutomationLog(): AutomationLog = AutomationLog(
        id = get(logId),
        ruleId = get(logRuleId),
        ruleName = get(logRuleName),
        status = get(logStatus) ?: "SUCCESS",
        message = get(logMessage),
        executedAt = localDateTime(logExecutedAt),
    )
}
