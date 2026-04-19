package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.revenue.RevenueAlertConfig
import com.ongo.domain.revenue.RevenueAlertConfigRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.REVENUE_ALERT_CONFIGS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class RevenueAlertConfigJooqRepository(
    private val dsl: DSLContext,
) : RevenueAlertConfigRepository {

    companion object {
        private val alertTypeField = DSL.field("alert_type", String::class.java)
        private val thresholdValueField = DSL.field("threshold_value", java.math.BigDecimal::class.java)
        private val scheduleTimeField = DSL.field("schedule_time", java.time.LocalTime::class.java)
        private val isEnabledField = DSL.field("is_enabled", Boolean::class.java)
    }

    override fun findByUserId(userId: Long): List<RevenueAlertConfig> =
        dsl.select()
            .from(REVENUE_ALERT_CONFIGS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toConfig() }

    override fun findById(id: Long): RevenueAlertConfig? =
        dsl.select()
            .from(REVENUE_ALERT_CONFIGS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toConfig()

    override fun findAllEnabledByAlertType(alertType: String): List<RevenueAlertConfig> =
        dsl.select()
            .from(REVENUE_ALERT_CONFIGS)
            .where(alertTypeField.eq(alertType))
            .and(isEnabledField.eq(true))
            .fetch()
            .map { it.toConfig() }

    override fun save(config: RevenueAlertConfig): RevenueAlertConfig {
        val id = dsl.insertInto(REVENUE_ALERT_CONFIGS)
            .set(USER_ID, config.userId)
            .set(alertTypeField, config.alertType)
            .set(thresholdValueField, config.thresholdValue)
            .set(scheduleTimeField, config.scheduleTime)
            .set(isEnabledField, config.isEnabled)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(config: RevenueAlertConfig): RevenueAlertConfig {
        dsl.update(REVENUE_ALERT_CONFIGS)
            .set(alertTypeField, config.alertType)
            .set(thresholdValueField, config.thresholdValue)
            .set(scheduleTimeField, config.scheduleTime)
            .set(isEnabledField, config.isEnabled)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(config.id))
            .execute()

        return findById(config.id!!)!!
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(REVENUE_ALERT_CONFIGS)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toConfig(): RevenueAlertConfig = RevenueAlertConfig(
        id = get(ID),
        userId = get(USER_ID),
        alertType = get(alertTypeField) ?: "",
        thresholdValue = get(thresholdValueField),
        scheduleTime = get(scheduleTimeField),
        isEnabled = get(isEnabledField) ?: true,
        createdAt = get(CREATED_AT),
        updatedAt = get(UPDATED_AT),
    )
}
