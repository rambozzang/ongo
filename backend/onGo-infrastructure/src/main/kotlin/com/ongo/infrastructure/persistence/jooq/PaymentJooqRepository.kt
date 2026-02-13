package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import com.ongo.domain.payment.Payment
import com.ongo.domain.payment.PaymentRepository
import com.ongo.infrastructure.persistence.jooq.Fields.AMOUNT
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.CURRENCY
import com.ongo.infrastructure.persistence.jooq.Fields.DESCRIPTION
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PAYMENT_METHOD
import com.ongo.infrastructure.persistence.jooq.Fields.PG_PROVIDER
import com.ongo.infrastructure.persistence.jooq.Fields.PG_TRANSACTION_ID
import com.ongo.infrastructure.persistence.jooq.Fields.RECEIPT_URL
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.PAYMENTS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class PaymentJooqRepository(
    private val dsl: DSLContext,
) : PaymentRepository {

    override fun findById(id: Long): Payment? =
        dsl.select()
            .from(PAYMENTS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toPayment()

    override fun findByUserId(userId: Long, page: Int, size: Int): List<Payment> =
        dsl.select()
            .from(PAYMENTS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .limit(size)
            .offset(page * size)
            .fetch()
            .map { it.toPayment() }

    override fun countByUserId(userId: Long): Long =
        dsl.selectCount()
            .from(PAYMENTS)
            .where(USER_ID.eq(userId))
            .fetchOne(0, Long::class.java) ?: 0L

    override fun save(payment: Payment): Payment {
        val record = dsl.insertInto(PAYMENTS)
            .set(USER_ID, payment.userId)
            .set(TYPE, payment.type.name)
            .set(AMOUNT, payment.amount)
            .set(CURRENCY, payment.currency)
            .set(STATUS, payment.status.name)
            .set(PG_PROVIDER, payment.pgProvider)
            .set(PG_TRANSACTION_ID, payment.pgTransactionId)
            .set(PAYMENT_METHOD, payment.paymentMethod)
            .set(RECEIPT_URL, payment.receiptUrl)
            .set(DESCRIPTION, payment.description)
            .returning()
            .fetchOne()!!

        return record.toPayment()
    }

    override fun update(payment: Payment): Payment {
        val record = dsl.update(PAYMENTS)
            .set(STATUS, payment.status.name)
            .set(PG_PROVIDER, payment.pgProvider)
            .set(PG_TRANSACTION_ID, payment.pgTransactionId)
            .set(PAYMENT_METHOD, payment.paymentMethod)
            .set(RECEIPT_URL, payment.receiptUrl)
            .set(DESCRIPTION, payment.description)
            .where(ID.eq(payment.id))
            .returning()
            .fetchOne()!!

        return record.toPayment()
    }

    private fun Record.toPayment(): Payment = Payment(
        id = get(ID),
        userId = get(USER_ID),
        type = PaymentType.valueOf(get(TYPE)),
        amount = get(AMOUNT),
        currency = get(CURRENCY),
        status = PaymentStatus.valueOf(get(STATUS)),
        pgProvider = get(PG_PROVIDER),
        pgTransactionId = get(PG_TRANSACTION_ID),
        paymentMethod = get(PAYMENT_METHOD),
        receiptUrl = get(RECEIPT_URL),
        description = get(DESCRIPTION),
        createdAt = localDateTime(CREATED_AT),
    )
}
