package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.revenue.RevenueInsight
import com.ongo.domain.revenue.RevenueInsightRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CONFIDENCE
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.REVENUE_INSIGHTS
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class RevenueInsightJooqRepository(
    private val dsl: DSLContext,
) : RevenueInsightRepository {

    companion object {
        private val insightTypeField = DSL.field("insight_type", String::class.java)
        private val contentJsonbField = DSL.field("content", JSONB::class.java)
    }

    override fun findByUserId(userId: Long, page: Int, size: Int): List<RevenueInsight> =
        dsl.select()
            .from(REVENUE_INSIGHTS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .limit(size)
            .offset(page * size)
            .fetch()
            .map { it.toInsight() }

    override fun countByUserId(userId: Long): Long =
        dsl.selectCount()
            .from(REVENUE_INSIGHTS)
            .where(USER_ID.eq(userId))
            .fetchOne()
            ?.get(0, Long::class.java) ?: 0L

    override fun save(insight: RevenueInsight): RevenueInsight {
        val id = dsl.insertInto(REVENUE_INSIGHTS)
            .set(USER_ID, insight.userId)
            .set(insightTypeField, insight.insightType)
            .set(contentJsonbField, JSONB.jsonb(insight.content))
            .set(PLATFORM, insight.platform)
            .set(CONFIDENCE, insight.confidence)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return dsl.select()
            .from(REVENUE_INSIGHTS)
            .where(ID.eq(id))
            .fetchOne()!!
            .toInsight()
    }

    private fun Record.toInsight(): RevenueInsight {
        val contentRaw = get("content")
        val contentStr = when (contentRaw) {
            is JSONB -> contentRaw.data()
            is String -> contentRaw
            else -> "{}"
        }
        return RevenueInsight(
            id = get(ID),
            userId = get(USER_ID),
            insightType = get(insightTypeField) ?: "",
            content = contentStr,
            platform = get(PLATFORM),
            confidence = get(CONFIDENCE),
            createdAt = get(CREATED_AT),
        )
    }
}
