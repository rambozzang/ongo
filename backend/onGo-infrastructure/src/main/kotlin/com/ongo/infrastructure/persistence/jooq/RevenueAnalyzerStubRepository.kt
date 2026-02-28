package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.revenueanalyzer.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
class RevenueProjectionJooqRepository(
    private val dsl: DSLContext,
) : RevenueProjectionRepository {

    companion object {
        private val TABLE = DSL.table("revenue_projections")
        private val CHANNEL_ID = DSL.field("channel_id", Long::class.java)
        private val SOURCE = DSL.field("source", String::class.java)
        private val CURRENT_MONTHLY = DSL.field("current_monthly", BigDecimal::class.java)
        private val PROJECTED_MONTHLY = DSL.field("projected_monthly", BigDecimal::class.java)
        private val CONFIDENCE = DSL.field("confidence", Int::class.java)
        private val PROJECTION_DATE = DSL.field("projection_date", String::class.java)
        private val FACTORS = DSL.field("factors", String::class.java)
    }

    override fun findByChannelId(channelId: Long): List<RevenueProjection> =
        dsl.select().from(TABLE)
            .where(CHANNEL_ID.eq(channelId))
            .fetch { it.toProjection() }

    override fun findByUserId(userId: Long): List<RevenueProjection> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .fetch { it.toProjection() }

    override fun save(projection: RevenueProjection): RevenueProjection {
        val factorsJson = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().writeValueAsString(projection.factors)
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, projection.userId)
            .set(CHANNEL_ID, projection.channelId)
            .set(SOURCE, projection.source)
            .set(CURRENT_MONTHLY, projection.currentMonthly)
            .set(PROJECTED_MONTHLY, projection.projectedMonthly)
            .set(CONFIDENCE, projection.confidence)
            .set(PROJECTION_DATE, projection.projectionDate)
            .set(FACTORS, DSL.field("?::jsonb", String::class.java, factorsJson))
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()!!.toProjection()
    }

    private fun Record.toProjection(): RevenueProjection {
        val factorsRaw = get("factors")
        val factorsStr = when (factorsRaw) {
            is String -> factorsRaw
            else -> factorsRaw?.toString() ?: "[]"
        }
        val factorsList = factorsStr.removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }

        return RevenueProjection(
            id = get(ID),
            userId = get(USER_ID),
            channelId = get(CHANNEL_ID),
            source = get(SOURCE),
            currentMonthly = get(CURRENT_MONTHLY) ?: BigDecimal.ZERO,
            projectedMonthly = get(PROJECTED_MONTHLY) ?: BigDecimal.ZERO,
            confidence = get(CONFIDENCE) ?: 0,
            projectionDate = get(PROJECTION_DATE),
            factors = factorsList,
            createdAt = localDateTime(CREATED_AT),
        )
    }
}

@Repository
class RevenueStreamJooqRepository(
    private val dsl: DSLContext,
) : RevenueStreamRepository {

    companion object {
        private val TABLE = DSL.table("revenue_streams")
        private val CHANNEL_ID = DSL.field("channel_id", Long::class.java)
        private val CHANNEL_NAME = DSL.field("channel_name", String::class.java)
        private val SOURCE = DSL.field("source", String::class.java)
        private val AMOUNT = DSL.field("amount", BigDecimal::class.java)
        private val CURRENCY = DSL.field("currency", String::class.java)
        private val PERIOD = DSL.field("period", String::class.java)
        private val GROWTH = DSL.field("growth", BigDecimal::class.java)
    }

    override fun findById(id: Long): RevenueStream? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toStream()

    override fun findByUserId(userId: Long): List<RevenueStream> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId)).fetch { it.toStream() }

    override fun findBySource(userId: Long, source: String): List<RevenueStream> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .and(SOURCE.eq(source))
            .fetch { it.toStream() }

    override fun save(stream: RevenueStream): RevenueStream {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, stream.userId)
            .set(CHANNEL_ID, stream.channelId)
            .set(CHANNEL_NAME, stream.channelName)
            .set(SOURCE, stream.source)
            .set(AMOUNT, stream.amount)
            .set(CURRENCY, stream.currency)
            .set(PERIOD, stream.period)
            .set(GROWTH, stream.growth)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(id)!!
    }

    private fun Record.toStream(): RevenueStream = RevenueStream(
        id = get(ID),
        userId = get(USER_ID),
        channelId = get(CHANNEL_ID),
        channelName = get(CHANNEL_NAME),
        source = get(SOURCE),
        amount = get(AMOUNT) ?: BigDecimal.ZERO,
        currency = get(CURRENCY) ?: "KRW",
        period = get(PERIOD),
        growth = get(GROWTH) ?: BigDecimal.ZERO,
        createdAt = localDateTime(CREATED_AT),
    )
}
