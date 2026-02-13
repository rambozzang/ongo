package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.CreditTransactionType
import com.ongo.domain.credit.AiCredit
import com.ongo.domain.credit.AiCreditTransaction
import com.ongo.domain.credit.AiPurchasedCredit
import com.ongo.domain.credit.CreditRepository
import com.ongo.infrastructure.persistence.jooq.Fields.AMOUNT
import com.ongo.infrastructure.persistence.jooq.Fields.BALANCE
import com.ongo.infrastructure.persistence.jooq.Fields.BALANCE_AFTER
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.EXPIRES_AT
import com.ongo.infrastructure.persistence.jooq.Fields.FEATURE
import com.ongo.infrastructure.persistence.jooq.Fields.FREE_MONTHLY
import com.ongo.infrastructure.persistence.jooq.Fields.FREE_REMAINING
import com.ongo.infrastructure.persistence.jooq.Fields.FREE_RESET_DATE
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PACKAGE_NAME
import com.ongo.infrastructure.persistence.jooq.Fields.PRICE
import com.ongo.infrastructure.persistence.jooq.Fields.PURCHASED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.REFERENCE_ID
import com.ongo.infrastructure.persistence.jooq.Fields.REMAINING
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS_TEXT
import com.ongo.infrastructure.persistence.jooq.Fields.TOTAL_CREDITS
import com.ongo.infrastructure.persistence.jooq.Fields.TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.AI_CREDITS
import com.ongo.infrastructure.persistence.jooq.Tables.AI_CREDIT_TRANSACTIONS
import com.ongo.infrastructure.persistence.jooq.Tables.AI_PURCHASED_CREDITS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.LocalDateTime

@Repository
class CreditJooqRepository(
    private val dsl: DSLContext,
) : CreditRepository {

    override fun findByUserId(userId: Long): AiCredit? =
        dsl.select()
            .from(AI_CREDITS)
            .where(USER_ID.eq(userId))
            .fetchOne()
            ?.toAiCredit()

    override fun findByUserIdForUpdate(userId: Long): AiCredit? =
        dsl.select()
            .from(AI_CREDITS)
            .where(USER_ID.eq(userId))
            .forUpdate()
            .fetchOne()
            ?.toAiCredit()

    override fun save(credit: AiCredit): AiCredit {
        val record = dsl.insertInto(AI_CREDITS)
            .set(USER_ID, credit.userId)
            .set(BALANCE, credit.balance)
            .set(FREE_MONTHLY, credit.freeMonthly)
            .set(FREE_REMAINING, credit.freeRemaining)
            .set(FREE_RESET_DATE, credit.freeResetDate)
            .returning()
            .fetchOne()!!

        return record.toAiCredit()
    }

    override fun update(credit: AiCredit): AiCredit {
        val record = dsl.update(AI_CREDITS)
            .set(BALANCE, credit.balance)
            .set(FREE_MONTHLY, credit.freeMonthly)
            .set(FREE_REMAINING, credit.freeRemaining)
            .set(FREE_RESET_DATE, credit.freeResetDate)
            .where(ID.eq(credit.id))
            .returning()
            .fetchOne()!!

        return record.toAiCredit()
    }

    override fun getTransactions(userId: Long, page: Int, size: Int): List<AiCreditTransaction> =
        dsl.select()
            .from(AI_CREDIT_TRANSACTIONS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .limit(size)
            .offset(page * size)
            .fetch()
            .map { it.toAiCreditTransaction() }

    override fun saveTransaction(transaction: AiCreditTransaction): AiCreditTransaction {
        val record = dsl.insertInto(AI_CREDIT_TRANSACTIONS)
            .set(USER_ID, transaction.userId)
            .set(TYPE, transaction.type.name)
            .set(AMOUNT, transaction.amount)
            .set(BALANCE_AFTER, transaction.balanceAfter)
            .set(FEATURE, transaction.feature)
            .set(REFERENCE_ID, transaction.referenceId)
            .returning()
            .fetchOne()!!

        return record.toAiCreditTransaction()
    }

    override fun findActivePurchasedCredits(userId: Long): List<AiPurchasedCredit> =
        dsl.select()
            .from(AI_PURCHASED_CREDITS)
            .where(USER_ID.eq(userId))
            .and(STATUS_TEXT.eq("ACTIVE"))
            .and(EXPIRES_AT.greaterThan(LocalDateTime.now()))
            .orderBy(EXPIRES_AT.asc())
            .fetch()
            .map { it.toAiPurchasedCredit() }

    override fun findActivePurchasedCreditsForUpdate(userId: Long): List<AiPurchasedCredit> =
        dsl.select()
            .from(AI_PURCHASED_CREDITS)
            .where(USER_ID.eq(userId))
            .and(STATUS_TEXT.eq("ACTIVE"))
            .and(EXPIRES_AT.greaterThan(LocalDateTime.now()))
            .orderBy(EXPIRES_AT.asc())
            .forUpdate()
            .fetch()
            .map { it.toAiPurchasedCredit() }

    override fun savePurchasedCredit(credit: AiPurchasedCredit): AiPurchasedCredit {
        val record = dsl.insertInto(AI_PURCHASED_CREDITS)
            .set(USER_ID, credit.userId)
            .set(PACKAGE_NAME, credit.packageName)
            .set(TOTAL_CREDITS, credit.totalCredits)
            .set(REMAINING, credit.remaining)
            .set(PRICE, credit.price)
            .set(EXPIRES_AT, credit.expiresAt)
            .set(STATUS, credit.status)
            .returning()
            .fetchOne()!!

        return record.toAiPurchasedCredit()
    }

    override fun updatePurchasedCredit(credit: AiPurchasedCredit): AiPurchasedCredit {
        val record = dsl.update(AI_PURCHASED_CREDITS)
            .set(REMAINING, credit.remaining)
            .set(STATUS, credit.status)
            .where(ID.eq(credit.id))
            .returning()
            .fetchOne()!!

        return record.toAiPurchasedCredit()
    }

    override fun findExpiredCredits(): List<AiPurchasedCredit> =
        dsl.select()
            .from(AI_PURCHASED_CREDITS)
            .where(STATUS_TEXT.eq("ACTIVE"))
            .and(EXPIRES_AT.lessOrEqual(LocalDateTime.now()))
            .fetch()
            .map { it.toAiPurchasedCredit() }

    override fun countTransactions(userId: Long): Long =
        dsl.selectCount()
            .from(AI_CREDIT_TRANSACTIONS)
            .where(USER_ID.eq(userId))
            .fetchOne(0, Long::class.java) ?: 0L

    override fun bulkExpirePurchasedCredits(): Int =
        dsl.update(AI_PURCHASED_CREDITS)
            .set(REMAINING, 0)
            .set(STATUS, "EXPIRED")
            .where(STATUS_TEXT.eq("ACTIVE"))
            .and(EXPIRES_AT.lessOrEqual(LocalDateTime.now()))
            .execute()

    override fun findUsersWithExpiredCredits(): List<Long> =
        dsl.selectDistinct(USER_ID)
            .from(AI_PURCHASED_CREDITS)
            .where(STATUS_TEXT.eq("EXPIRED"))
            .and(EXPIRES_AT.greaterThan(LocalDateTime.now().minusDays(1)))
            .fetch()
            .map { it.get(USER_ID) }

    override fun findLowCreditUsers(): List<AiCredit> =
        dsl.select()
            .from(AI_CREDITS)
            .where(FREE_MONTHLY.greaterThan(0))
            .and(BALANCE.greaterThan(0))
            .and(BALANCE.lessOrEqual(
                DSL.field("({0} * 0.2)", Int::class.java, FREE_MONTHLY)
            ))
            .fetch()
            .map { it.toAiCredit() }

    override fun findUsersForFreeReset(today: LocalDate): List<Long> =
        dsl.select(USER_ID)
            .from(AI_CREDITS)
            .where(FREE_RESET_DATE.lessOrEqual(today))
            .fetch()
            .map { it.get(USER_ID) }

    private fun Record.toAiCredit(): AiCredit = AiCredit(
        id = get(ID),
        userId = get(USER_ID),
        balance = get(BALANCE),
        freeMonthly = get(FREE_MONTHLY),
        freeRemaining = get(FREE_REMAINING),
        freeResetDate = localDate(FREE_RESET_DATE)!!,
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )

    private fun Record.toAiCreditTransaction(): AiCreditTransaction = AiCreditTransaction(
        id = get(ID),
        userId = get(USER_ID),
        type = CreditTransactionType.valueOf(get(TYPE)),
        amount = get(AMOUNT),
        balanceAfter = get(BALANCE_AFTER),
        feature = get(FEATURE),
        referenceId = get(REFERENCE_ID),
        createdAt = localDateTime(CREATED_AT),
    )

    private fun Record.toAiPurchasedCredit(): AiPurchasedCredit = AiPurchasedCredit(
        id = get(ID),
        userId = get(USER_ID),
        packageName = get(PACKAGE_NAME),
        totalCredits = get(TOTAL_CREDITS),
        remaining = get(REMAINING),
        price = get(PRICE),
        purchasedAt = localDateTime(PURCHASED_AT),
        expiresAt = localDateTime(EXPIRES_AT)!!,
        status = get(STATUS),
    )
}
