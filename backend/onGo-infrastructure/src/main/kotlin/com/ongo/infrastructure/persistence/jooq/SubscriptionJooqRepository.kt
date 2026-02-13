package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.infrastructure.persistence.jooq.Fields.BILLING_CYCLE
import com.ongo.infrastructure.persistence.jooq.Fields.CANCELLED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.CURRENT_PERIOD_END
import com.ongo.infrastructure.persistence.jooq.Fields.CURRENT_PERIOD_START
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.NEXT_BILLING_DATE
import com.ongo.infrastructure.persistence.jooq.Fields.PLAN_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.PRICE
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS_TEXT
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.SUBSCRIPTIONS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class SubscriptionJooqRepository(
    private val dsl: DSLContext,
) : SubscriptionRepository {

    override fun findByUserId(userId: Long): Subscription? =
        dsl.select()
            .from(SUBSCRIPTIONS)
            .where(USER_ID.eq(userId))
            .fetchOne()
            ?.toSubscription()

    override fun save(subscription: Subscription): Subscription {
        val record = dsl.insertInto(SUBSCRIPTIONS)
            .set(USER_ID, subscription.userId)
            .set(PLAN_TYPE, subscription.planType.name)
            .set(STATUS, subscription.status.name)
            .set(PRICE, subscription.price)
            .set(BILLING_CYCLE, subscription.billingCycle.name)
            .set(CURRENT_PERIOD_START, subscription.currentPeriodStart)
            .set(CURRENT_PERIOD_END, subscription.currentPeriodEnd)
            .set(NEXT_BILLING_DATE, subscription.nextBillingDate)
            .set(CANCELLED_AT, subscription.cancelledAt)
            .returning()
            .fetchOne()!!

        return record.toSubscription()
    }

    override fun update(subscription: Subscription): Subscription {
        val record = dsl.update(SUBSCRIPTIONS)
            .set(PLAN_TYPE, subscription.planType.name)
            .set(STATUS, subscription.status.name)
            .set(PRICE, subscription.price)
            .set(BILLING_CYCLE, subscription.billingCycle.name)
            .set(CURRENT_PERIOD_START, subscription.currentPeriodStart)
            .set(CURRENT_PERIOD_END, subscription.currentPeriodEnd)
            .set(NEXT_BILLING_DATE, subscription.nextBillingDate)
            .set(CANCELLED_AT, subscription.cancelledAt)
            .where(ID.eq(subscription.id))
            .returning()
            .fetchOne()!!

        return record.toSubscription()
    }

    override fun findDueForBilling(now: LocalDateTime): List<Subscription> =
        dsl.select()
            .from(SUBSCRIPTIONS)
            .where(STATUS_TEXT.eq(SubscriptionStatus.ACTIVE.name))
            .and(NEXT_BILLING_DATE.lessOrEqual(now))
            .orderBy(NEXT_BILLING_DATE.asc())
            .fetch()
            .map { it.toSubscription() }

    override fun findPastDue(gracePeriodDays: Int): List<Subscription> {
        val cutoff = LocalDateTime.now().minusDays(gracePeriodDays.toLong())

        return dsl.select()
            .from(SUBSCRIPTIONS)
            .where(STATUS_TEXT.eq(SubscriptionStatus.PAST_DUE.name))
            .and(NEXT_BILLING_DATE.lessThan(cutoff))
            .fetch()
            .map { it.toSubscription() }
    }

    override fun findByPlanType(planType: PlanType): List<Subscription> =
        dsl.select()
            .from(SUBSCRIPTIONS)
            .where(PLAN_TYPE.eq(planType.name))
            .and(STATUS_TEXT.`in`(SubscriptionStatus.ACTIVE.name, SubscriptionStatus.FREE.name))
            .fetch()
            .map { it.toSubscription() }

    private fun Record.toSubscription(): Subscription {
        val billingCycleStr = get(BILLING_CYCLE)

        return Subscription(
            id = get(ID),
            userId = get(USER_ID),
            planType = PlanType.valueOf(get(PLAN_TYPE)),
            status = SubscriptionStatus.valueOf(get(STATUS)),
            price = get(PRICE),
            billingCycle = if (billingCycleStr != null) BillingCycle.valueOf(billingCycleStr) else BillingCycle.MONTHLY,
            currentPeriodStart = localDateTime(CURRENT_PERIOD_START),
            currentPeriodEnd = localDateTime(CURRENT_PERIOD_END),
            nextBillingDate = localDateTime(NEXT_BILLING_DATE),
            cancelledAt = localDateTime(CANCELLED_AT),
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
    }
}
