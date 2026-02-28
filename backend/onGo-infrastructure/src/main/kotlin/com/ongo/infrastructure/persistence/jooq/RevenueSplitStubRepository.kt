package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.revenuesplit.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
class RevenueSplitJooqRepository(
    private val dsl: DSLContext,
) : RevenueSplitRepository {

    companion object {
        private val TABLE = DSL.table("revenue_splits")
        private val DESCRIPTION = DSL.field("description", String::class.java)
        private val TOTAL_AMOUNT = DSL.field("total_amount", Long::class.java)
        private val CURRENCY = DSL.field("currency", String::class.java)
        private val PERIOD = DSL.field("period", String::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<RevenueSplit> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .fetch { it.toSplit() }

    override fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<RevenueSplit> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(STATUS.eq(status))
            .fetch { it.toSplit() }

    override fun findById(id: Long): RevenueSplit? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toSplit()

    override fun save(split: RevenueSplit): RevenueSplit {
        val id = dsl.insertInto(TABLE)
            .set(WORKSPACE_ID, split.workspaceId)
            .set(TITLE, split.title)
            .set(DESCRIPTION, split.description)
            .set(TOTAL_AMOUNT, split.totalAmount)
            .set(CURRENCY, split.currency)
            .set(STATUS, split.status)
            .set(PERIOD, split.period)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(id)!!
    }

    override fun update(split: RevenueSplit): RevenueSplit {
        dsl.update(TABLE)
            .set(TITLE, split.title)
            .set(DESCRIPTION, split.description)
            .set(TOTAL_AMOUNT, split.totalAmount)
            .set(CURRENCY, split.currency)
            .set(STATUS, split.status)
            .set(PERIOD, split.period)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(split.id))
            .execute()
        return findById(split.id)!!
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toSplit(): RevenueSplit = RevenueSplit(
        id = get(ID),
        workspaceId = get(WORKSPACE_ID),
        title = get(TITLE),
        description = get(DESCRIPTION),
        totalAmount = get(TOTAL_AMOUNT) ?: 0,
        currency = get(CURRENCY) ?: "KRW",
        status = get(STATUS) ?: "DRAFT",
        period = get(PERIOD),
        createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
        updatedAt = localDateTime(UPDATED_AT) ?: LocalDateTime.now(),
    )
}

@Repository
class SplitMemberJooqRepository(
    private val dsl: DSLContext,
) : SplitMemberRepository {

    companion object {
        private val TABLE = DSL.table("split_members")
        private val SPLIT_ID = DSL.field("split_id", Long::class.java)
        private val NAME = DSL.field("name", String::class.java)
        private val EMAIL = DSL.field("email", String::class.java)
        private val ROLE = DSL.field("role", String::class.java)
        private val PERCENTAGE = DSL.field("percentage", BigDecimal::class.java)
        private val AMOUNT = DSL.field("amount", Long::class.java)
        private val PAYMENT_STATUS = DSL.field("payment_status", String::class.java)
        private val PAID_AT = DSL.field("paid_at", LocalDateTime::class.java)
    }

    override fun findBySplitId(splitId: Long): List<SplitMember> =
        dsl.select().from(TABLE)
            .where(SPLIT_ID.eq(splitId))
            .fetch { it.toMember() }

    override fun findById(id: Long): SplitMember? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toMember()

    override fun save(member: SplitMember): SplitMember {
        val id = dsl.insertInto(TABLE)
            .set(SPLIT_ID, member.splitId)
            .set(NAME, member.name)
            .set(EMAIL, member.email)
            .set(ROLE, member.role)
            .set(PERCENTAGE, member.percentage)
            .set(AMOUNT, member.amount)
            .set(PAYMENT_STATUS, member.paymentStatus)
            .set(PAID_AT, member.paidAt)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(id)!!
    }

    override fun saveBatch(members: List<SplitMember>): List<SplitMember> {
        if (members.isEmpty()) return emptyList()
        val splitId = members.first().splitId
        members.forEach { member ->
            dsl.insertInto(TABLE)
                .set(SPLIT_ID, member.splitId)
                .set(NAME, member.name)
                .set(EMAIL, member.email)
                .set(ROLE, member.role)
                .set(PERCENTAGE, member.percentage)
                .set(AMOUNT, member.amount)
                .set(PAYMENT_STATUS, member.paymentStatus)
                .set(PAID_AT, member.paidAt)
                .execute()
        }
        return findBySplitId(splitId)
    }

    override fun update(member: SplitMember): SplitMember {
        dsl.update(TABLE)
            .set(NAME, member.name)
            .set(EMAIL, member.email)
            .set(ROLE, member.role)
            .set(PERCENTAGE, member.percentage)
            .set(AMOUNT, member.amount)
            .set(PAYMENT_STATUS, member.paymentStatus)
            .set(PAID_AT, member.paidAt)
            .where(ID.eq(member.id))
            .execute()
        return findById(member.id)!!
    }

    override fun deleteBySplitId(splitId: Long) {
        dsl.deleteFrom(TABLE).where(SPLIT_ID.eq(splitId)).execute()
    }

    private fun Record.toMember(): SplitMember = SplitMember(
        id = get(ID),
        splitId = get(SPLIT_ID),
        name = get(NAME),
        email = get(EMAIL),
        role = get(ROLE),
        percentage = get(PERCENTAGE) ?: BigDecimal.ZERO,
        amount = get(AMOUNT) ?: 0,
        paymentStatus = get(PAYMENT_STATUS) ?: "PENDING",
        paidAt = localDateTime(PAID_AT),
        createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
    )
}
