package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.analytics.LiveAlertConfig
import com.ongo.domain.analytics.LiveAlertConfigRepository
import com.ongo.infrastructure.persistence.jooq.Fields.ENABLED
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.LIVE_ALERT_CONFIGS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class LiveAlertConfigJooqRepository(
    private val dsl: DSLContext,
) : LiveAlertConfigRepository {

    companion object {
        private val typeField = DSL.field("type", String::class.java)
        private val thresholdField = DSL.field("threshold", Int::class.java)
    }

    override fun findByUserId(userId: Long): List<LiveAlertConfig> =
        dsl.select()
            .from(LIVE_ALERT_CONFIGS)
            .where(USER_ID.eq(userId))
            .fetch()
            .map { it.toConfig() }

    override fun findById(id: Long): LiveAlertConfig? =
        dsl.select()
            .from(LIVE_ALERT_CONFIGS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toConfig()

    override fun save(config: LiveAlertConfig): LiveAlertConfig {
        val id = dsl.insertInto(LIVE_ALERT_CONFIGS)
            .set(USER_ID, config.userId)
            .set(typeField, config.type)
            .set(ENABLED, config.enabled)
            .set(thresholdField, config.threshold)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(config: LiveAlertConfig): LiveAlertConfig {
        dsl.update(LIVE_ALERT_CONFIGS)
            .set(typeField, config.type)
            .set(ENABLED, config.enabled)
            .set(thresholdField, config.threshold)
            .where(ID.eq(config.id))
            .execute()

        return findById(config.id!!)!!
    }

    private fun Record.toConfig(): LiveAlertConfig = LiveAlertConfig(
        id = get(ID),
        userId = get(USER_ID),
        type = get(typeField) ?: "",
        enabled = get(ENABLED) ?: true,
        threshold = get(thresholdField) ?: 0,
    )
}
