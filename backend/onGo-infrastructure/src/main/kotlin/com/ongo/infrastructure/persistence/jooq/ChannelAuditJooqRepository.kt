package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.channelaudit.ChannelAuditReport
import com.ongo.domain.channelaudit.ChannelAuditRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.OVERALL_SCORE
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.CHANNEL_AUDIT_REPORTS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class ChannelAuditJooqRepository(
    private val dsl: DSLContext,
) : ChannelAuditRepository {

    companion object {
        private val CONTENT = DSL.field("content", String::class.java)
    }

    override fun findByUserId(userId: Long, page: Int, size: Int): List<ChannelAuditReport> =
        dsl.select()
            .from(CHANNEL_AUDIT_REPORTS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .limit(size)
            .offset(page * size)
            .fetch()
            .map { it.toReport() }

    override fun countByUserId(userId: Long): Int =
        dsl.selectCount()
            .from(CHANNEL_AUDIT_REPORTS)
            .where(USER_ID.eq(userId))
            .fetchOne(0, Int::class.java) ?: 0

    override fun save(report: ChannelAuditReport): ChannelAuditReport {
        val id = dsl.insertInto(CHANNEL_AUDIT_REPORTS)
            .set(USER_ID, report.userId)
            .set(OVERALL_SCORE, report.overallScore)
            .set(DSL.field("content"), DSL.field("?::jsonb", String::class.java, report.content))
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun findById(id: Long): ChannelAuditReport? =
        dsl.select()
            .from(CHANNEL_AUDIT_REPORTS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toReport()

    private fun Record.toReport(): ChannelAuditReport {
        val contentRaw = get("content")
        return ChannelAuditReport(
            id = get(ID),
            userId = get(USER_ID),
            overallScore = get(OVERALL_SCORE) ?: 0,
            content = when (contentRaw) {
                is String -> contentRaw
                else -> contentRaw?.toString() ?: "{}"
            },
            createdAt = localDateTime(CREATED_AT) ?: java.time.LocalDateTime.now(),
        )
    }
}
