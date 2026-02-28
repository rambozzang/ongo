package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.brandsafety.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class BrandSafetyCheckJooqRepository(
    private val dsl: DSLContext,
) : BrandSafetyCheckRepository {

    companion object {
        private val TABLE = DSL.table("brand_safety_checks")
        private val CONTENT_TITLE = DSL.field("content_title", String::class.java)
        private val CONTENT_TYPE = DSL.field("content_type", String::class.java)
        private val PLATFORM = DSL.field("platform", String::class.java)
        private val OVERALL_SCORE = DSL.field("overall_score", Int::class.java)
        private val CHECKED_AT = DSL.field("checked_at", LocalDateTime::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<BrandSafetyCheck> =
        dsl.select().from(TABLE).where(WORKSPACE_ID.eq(workspaceId)).fetch().map { it.toBrandSafetyCheck() }

    override fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<BrandSafetyCheck> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(STATUS.eq(status))
            .fetch().map { it.toBrandSafetyCheck() }

    override fun findById(id: Long): BrandSafetyCheck? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toBrandSafetyCheck()

    override fun save(check: BrandSafetyCheck): BrandSafetyCheck {
        val newId = dsl.insertInto(TABLE)
            .set(WORKSPACE_ID, check.workspaceId)
            .set(CONTENT_TITLE, check.contentTitle)
            .set(CONTENT_TYPE, check.contentType)
            .set(PLATFORM, check.platform)
            .set(STATUS, check.status)
            .set(OVERALL_SCORE, check.overallScore)
            .set(CHECKED_AT, check.checkedAt)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(newId)!!
    }

    override fun update(check: BrandSafetyCheck): BrandSafetyCheck {
        dsl.update(TABLE)
            .set(CONTENT_TITLE, check.contentTitle)
            .set(CONTENT_TYPE, check.contentType)
            .set(PLATFORM, check.platform)
            .set(STATUS, check.status)
            .set(OVERALL_SCORE, check.overallScore)
            .set(CHECKED_AT, check.checkedAt)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(check.id))
            .execute()
        return findById(check.id)!!
    }

    private fun Record.toBrandSafetyCheck(): BrandSafetyCheck =
        BrandSafetyCheck(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            contentTitle = get(CONTENT_TITLE),
            contentType = get(CONTENT_TYPE),
            platform = get(PLATFORM),
            status = get(STATUS) ?: "PENDING",
            overallScore = get(OVERALL_SCORE) ?: 0,
            checkedAt = localDateTime(CHECKED_AT),
            createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
            updatedAt = localDateTime(UPDATED_AT) ?: LocalDateTime.now(),
        )
}

@Repository
class BrandSafetyRuleJooqRepository(
    private val dsl: DSLContext,
) : BrandSafetyRuleRepository {

    companion object {
        private val TABLE = DSL.table("brand_safety_rules")
        private val NAME = DSL.field("name", String::class.java)
        private val CATEGORY = DSL.field("category", String::class.java)
        private val DESCRIPTION = DSL.field("description", String::class.java)
        private val IS_ENABLED = DSL.field("is_enabled", Boolean::class.java)
        private val SEVERITY = DSL.field("severity", String::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<BrandSafetyRule> =
        dsl.select().from(TABLE).where(WORKSPACE_ID.eq(workspaceId)).fetch().map { it.toBrandSafetyRule() }

    override fun findById(id: Long): BrandSafetyRule? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toBrandSafetyRule()

    override fun save(rule: BrandSafetyRule): BrandSafetyRule {
        val newId = dsl.insertInto(TABLE)
            .set(WORKSPACE_ID, rule.workspaceId)
            .set(NAME, rule.name)
            .set(CATEGORY, rule.category)
            .set(DESCRIPTION, rule.description)
            .set(IS_ENABLED, rule.isEnabled)
            .set(SEVERITY, rule.severity)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(newId)!!
    }

    override fun update(rule: BrandSafetyRule): BrandSafetyRule {
        dsl.update(TABLE)
            .set(NAME, rule.name)
            .set(CATEGORY, rule.category)
            .set(DESCRIPTION, rule.description)
            .set(IS_ENABLED, rule.isEnabled)
            .set(SEVERITY, rule.severity)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(rule.id))
            .execute()
        return findById(rule.id)!!
    }

    private fun Record.toBrandSafetyRule(): BrandSafetyRule =
        BrandSafetyRule(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            name = get(NAME),
            category = get(CATEGORY),
            description = get(DESCRIPTION),
            isEnabled = get(IS_ENABLED) ?: true,
            severity = get(SEVERITY) ?: "MEDIUM",
            createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
            updatedAt = localDateTime(UPDATED_AT) ?: LocalDateTime.now(),
        )
}

@Repository
class SafetyCheckItemJooqRepository(
    private val dsl: DSLContext,
) : SafetyCheckItemRepository {

    companion object {
        private val TABLE = DSL.table("safety_check_items")
        private val CHECK_ID = DSL.field("check_id", Long::class.java)
        private val CATEGORY = DSL.field("category", String::class.java)
        private val NAME = DSL.field("name", String::class.java)
        private val SEVERITY = DSL.field("severity", String::class.java)
        private val DESCRIPTION = DSL.field("description", String::class.java)
        private val RECOMMENDATION = DSL.field("recommendation", String::class.java)
    }

    override fun findByCheckId(checkId: Long): List<SafetyCheckItem> =
        dsl.select().from(TABLE).where(CHECK_ID.eq(checkId)).fetch().map { it.toSafetyCheckItem() }

    override fun save(item: SafetyCheckItem): SafetyCheckItem {
        val newId = dsl.insertInto(TABLE)
            .set(CHECK_ID, item.checkId)
            .set(CATEGORY, item.category)
            .set(NAME, item.name)
            .set(STATUS, item.status)
            .set(SEVERITY, item.severity)
            .set(DESCRIPTION, item.description)
            .set(RECOMMENDATION, item.recommendation)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return dsl.select().from(TABLE).where(ID.eq(newId)).fetchOne()!!.toSafetyCheckItem()
    }

    override fun saveBatch(items: List<SafetyCheckItem>): List<SafetyCheckItem> =
        items.map { save(it) }

    private fun Record.toSafetyCheckItem(): SafetyCheckItem =
        SafetyCheckItem(
            id = get(ID),
            checkId = get(CHECK_ID),
            category = get(CATEGORY),
            name = get(NAME),
            status = get(STATUS) ?: "PASS",
            severity = get(SEVERITY) ?: "LOW",
            description = get(DESCRIPTION),
            recommendation = get(RECOMMENDATION),
            createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
        )
}
