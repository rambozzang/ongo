package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.usage.UsageAlertConfig
import com.ongo.domain.usage.UsageAlertRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ENABLED
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.LAST_ALERTED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.THRESHOLD_PERCENT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.USAGE_ALERT_CONFIGS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class UsageAlertJooqRepository(
    private val dsl: DSLContext,
) : UsageAlertRepository {

    companion object {
        private val ALERT_TYPE_FIELD = DSL.field("alert_type", String::class.java)
    }

    override fun findByUserId(userId: Long): List<UsageAlertConfig> =
        dsl.select().from(USAGE_ALERT_CONFIGS)
            .where(USER_ID.eq(userId))
            .fetch().map { it.toConfig() }

    override fun findByUserIdAndType(userId: Long, alertType: String): UsageAlertConfig? =
        dsl.select().from(USAGE_ALERT_CONFIGS)
            .where(USER_ID.eq(userId))
            .and(ALERT_TYPE_FIELD.eq(alertType))
            .fetchOne()?.toConfig()

    override fun save(config: UsageAlertConfig): UsageAlertConfig {
        val id = dsl.insertInto(USAGE_ALERT_CONFIGS)
            .set(USER_ID, config.userId)
            .set(ALERT_TYPE_FIELD, config.alertType)
            .set(THRESHOLD_PERCENT, config.thresholdPercent)
            .set(ENABLED, config.enabled)
            .set(LAST_ALERTED_AT, config.lastAlertedAt)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)
        return config.copy(id = id)
    }

    override fun update(config: UsageAlertConfig) {
        dsl.update(USAGE_ALERT_CONFIGS)
            .set(THRESHOLD_PERCENT, config.thresholdPercent)
            .set(ENABLED, config.enabled)
            .set(LAST_ALERTED_AT, config.lastAlertedAt)
            .where(ID.eq(config.id))
            .execute()
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(USAGE_ALERT_CONFIGS).where(ID.eq(id)).execute()
    }

    private fun Record.toConfig(): UsageAlertConfig = UsageAlertConfig(
        id = get(ID),
        userId = get(USER_ID),
        alertType = get(ALERT_TYPE_FIELD) ?: "",
        thresholdPercent = get(THRESHOLD_PERCENT) ?: 80,
        enabled = get(ENABLED) ?: true,
        lastAlertedAt = localDateTime(LAST_ALERTED_AT),
        createdAt = localDateTime(CREATED_AT),
    )
}
