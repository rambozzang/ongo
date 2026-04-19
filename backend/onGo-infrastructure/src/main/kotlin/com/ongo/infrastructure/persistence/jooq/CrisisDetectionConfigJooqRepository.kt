package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.comment.CrisisDetectionConfig
import com.ongo.domain.comment.CrisisDetectionConfigRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class CrisisDetectionConfigJooqRepository(
    private val dsl: DSLContext,
) : CrisisDetectionConfigRepository {

    companion object {
        private val TABLE = DSL.table("crisis_detection_configs")
        private val NEGATIVE_THRESHOLD_PCT = DSL.field("negative_threshold_pct", Int::class.java)
        private val WINDOW_HOURS = DSL.field("window_hours", Int::class.java)
        private val IS_ENABLED = DSL.field("is_enabled", Boolean::class.java)
    }

    override fun findByUserId(userId: Long): CrisisDetectionConfig? =
        dsl.select()
            .from(TABLE)
            .where(USER_ID.eq(userId))
            .fetchOne()
            ?.toConfig()

    override fun save(config: CrisisDetectionConfig): CrisisDetectionConfig {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, config.userId)
            .set(NEGATIVE_THRESHOLD_PCT, config.negativeThresholdPct)
            .set(WINDOW_HOURS, config.windowHours)
            .set(IS_ENABLED, config.isEnabled)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()!!.toConfig()
    }

    override fun update(config: CrisisDetectionConfig): CrisisDetectionConfig {
        dsl.update(TABLE)
            .set(NEGATIVE_THRESHOLD_PCT, config.negativeThresholdPct)
            .set(WINDOW_HOURS, config.windowHours)
            .set(IS_ENABLED, config.isEnabled)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(config.id))
            .execute()
        return dsl.select().from(TABLE).where(ID.eq(config.id)).fetchOne()!!.toConfig()
    }

    private fun Record.toConfig(): CrisisDetectionConfig = CrisisDetectionConfig(
        id = get(ID),
        userId = get(USER_ID),
        negativeThresholdPct = get(NEGATIVE_THRESHOLD_PCT) ?: 300,
        windowHours = get(WINDOW_HOURS) ?: 24,
        isEnabled = get(IS_ENABLED) ?: true,
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )
}
