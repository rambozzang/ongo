package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.fanfunding.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class FundingGoalJooqRepository(
    private val dsl: DSLContext,
) : FundingGoalRepository {

    companion object {
        private val TABLE = DSL.table("funding_goals")
        private val TARGET_AMOUNT = DSL.field("target_amount", Long::class.java)
        private val CURRENT_AMOUNT = DSL.field("current_amount", Long::class.java)
        private val DEADLINE = DSL.field("deadline", LocalDateTime::class.java)
        private val IS_ACTIVE = DSL.field("is_active", Boolean::class.java)
    }

    override fun findById(id: Long): FundingGoal? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toGoal()

    override fun findByUserId(userId: Long): List<FundingGoal> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId)).fetch { it.toGoal() }

    override fun save(goal: FundingGoal): FundingGoal {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, goal.userId)
            .set(TITLE, goal.title)
            .set(TARGET_AMOUNT, goal.targetAmount)
            .set(CURRENT_AMOUNT, goal.currentAmount)
            .set(DEADLINE, goal.deadline)
            .set(IS_ACTIVE, goal.isActive)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(id)!!
    }

    override fun update(goal: FundingGoal): FundingGoal {
        dsl.update(TABLE)
            .set(TITLE, goal.title)
            .set(TARGET_AMOUNT, goal.targetAmount)
            .set(CURRENT_AMOUNT, goal.currentAmount)
            .set(DEADLINE, goal.deadline)
            .set(IS_ACTIVE, goal.isActive)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(goal.id))
            .execute()
        return findById(goal.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toGoal(): FundingGoal = FundingGoal(
        id = get(ID),
        userId = get(USER_ID),
        title = get(TITLE),
        targetAmount = get(TARGET_AMOUNT) ?: 0,
        currentAmount = get(CURRENT_AMOUNT) ?: 0,
        deadline = localDateTime(DEADLINE),
        isActive = get(IS_ACTIVE) ?: true,
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )
}

@Repository
class FundingTransactionJooqRepository(
    private val dsl: DSLContext,
) : FundingTransactionRepository {

    companion object {
        private val TABLE = DSL.table("funding_transactions")
        private val SOURCE = DSL.field("source", String::class.java)
        private val PLATFORM = DSL.field("platform", String::class.java)
        private val AMOUNT = DSL.field("amount", Long::class.java)
        private val CURRENCY = DSL.field("currency", String::class.java)
        private val DONOR_NAME = DSL.field("donor_name", String::class.java)
        private val MESSAGE = DSL.field("message", String::class.java)
        private val VIDEO_ID_STR = DSL.field("video_id", String::class.java)
        private val VIDEO_TITLE = DSL.field("video_title", String::class.java)
    }

    override fun findById(id: Long): FundingTransaction? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toTransaction()

    override fun findByUserId(userId: Long): List<FundingTransaction> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId)).fetch { it.toTransaction() }

    override fun findByUserIdAndPeriod(userId: Long, days: Int): List<FundingTransaction> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .and(CREATED_AT.ge(LocalDateTime.now().minusDays(days.toLong())))
            .fetch { it.toTransaction() }

    override fun findByUserIdAndFilter(userId: Long, source: String?, platform: String?, dateFrom: String?, dateTo: String?): List<FundingTransaction> {
        var query = dsl.select().from(TABLE).where(USER_ID.eq(userId))
        if (source != null) query = query.and(SOURCE.eq(source))
        if (platform != null) query = query.and(PLATFORM.eq(platform))
        if (dateFrom != null) query = query.and(CREATED_AT.ge(java.time.LocalDate.parse(dateFrom).atStartOfDay()))
        if (dateTo != null) query = query.and(CREATED_AT.le(java.time.LocalDate.parse(dateTo).atTime(23, 59, 59)))
        return query.fetch { it.toTransaction() }
    }

    override fun save(transaction: FundingTransaction): FundingTransaction {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, transaction.userId)
            .set(SOURCE, transaction.source)
            .set(PLATFORM, transaction.platform)
            .set(AMOUNT, transaction.amount)
            .set(CURRENCY, transaction.currency)
            .set(DONOR_NAME, transaction.donorName)
            .set(MESSAGE, transaction.message)
            .set(VIDEO_ID_STR, transaction.videoId)
            .set(VIDEO_TITLE, transaction.videoTitle)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(id)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toTransaction(): FundingTransaction = FundingTransaction(
        id = get(ID),
        userId = get(USER_ID),
        source = get(SOURCE) ?: "SUPER_CHAT",
        platform = get(PLATFORM),
        amount = get(AMOUNT) ?: 0,
        currency = get(CURRENCY) ?: "KRW",
        donorName = get(DONOR_NAME),
        message = get(MESSAGE),
        videoId = get(VIDEO_ID_STR),
        videoTitle = get(VIDEO_TITLE),
        createdAt = localDateTime(CREATED_AT),
    )
}
