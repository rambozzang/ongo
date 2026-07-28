package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.infrastructure.persistence.jooq.Fields.BILLING_CYCLE
import com.ongo.infrastructure.persistence.jooq.Fields.CANCELLED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.PADDLE_CUSTOMER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.PADDLE_SUBSCRIPTION_ID
import com.ongo.infrastructure.persistence.jooq.Fields.PAUSED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.PENDING_PLAN_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.CURRENT_PERIOD_END
import com.ongo.infrastructure.persistence.jooq.Fields.CURRENT_PERIOD_START
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.NEXT_BILLING_DATE
import com.ongo.infrastructure.persistence.jooq.Fields.PLAN_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.PRICE
import com.ongo.infrastructure.persistence.jooq.Fields.RESUME_AT
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS_TEXT
import com.ongo.infrastructure.persistence.jooq.Fields.STORAGE_QUOTA_LIMIT_BYTES
import com.ongo.infrastructure.persistence.jooq.Fields.TRIAL_END
import com.ongo.infrastructure.persistence.jooq.Fields.TRIAL_PLAN_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.TRIAL_START
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

    override fun findByPaddleSubscriptionId(paddleSubscriptionId: String): Subscription? =
        dsl.select()
            .from(SUBSCRIPTIONS)
            .where(PADDLE_SUBSCRIPTION_ID.eq(paddleSubscriptionId))
            .fetchOne()
            ?.toSubscription()

    private fun findById(id: Long): Subscription? =
        dsl.select()
            .from(SUBSCRIPTIONS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toSubscription()

    override fun save(subscription: Subscription): Subscription {
        val id = dsl.insertInto(SUBSCRIPTIONS)
            .set(USER_ID, subscription.userId)
            .set(PLAN_TYPE, subscription.planType.name)
            .set(STATUS, subscription.status.name)
            .set(PRICE, subscription.price)
            .set(BILLING_CYCLE, subscription.billingCycle.name)
            .set(CURRENT_PERIOD_START, subscription.currentPeriodStart)
            .set(CURRENT_PERIOD_END, subscription.currentPeriodEnd)
            .set(NEXT_BILLING_DATE, subscription.nextBillingDate)
            .set(PENDING_PLAN_TYPE, subscription.pendingPlanType?.name)
            .set(STORAGE_QUOTA_LIMIT_BYTES, subscription.storageQuotaLimitBytes)
            .set(PADDLE_SUBSCRIPTION_ID, subscription.paddleSubscriptionId)
            .set(PADDLE_CUSTOMER_ID, subscription.paddleCustomerId)
            .set(CANCELLED_AT, subscription.cancelledAt)
            .set(TRIAL_START, subscription.trialStart)
            .set(TRIAL_END, subscription.trialEnd)
            .set(TRIAL_PLAN_TYPE, subscription.trialPlanType?.name)
            .set(PAUSED_AT, subscription.pausedAt)
            .set(RESUME_AT, subscription.resumeAt)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(subscription: Subscription): Subscription {
        dsl.update(SUBSCRIPTIONS)
            .set(PLAN_TYPE, subscription.planType.name)
            .set(STATUS, subscription.status.name)
            .set(PRICE, subscription.price)
            .set(BILLING_CYCLE, subscription.billingCycle.name)
            .set(CURRENT_PERIOD_START, subscription.currentPeriodStart)
            .set(CURRENT_PERIOD_END, subscription.currentPeriodEnd)
            .set(NEXT_BILLING_DATE, subscription.nextBillingDate)
            .set(PENDING_PLAN_TYPE, subscription.pendingPlanType?.name)
            .set(STORAGE_QUOTA_LIMIT_BYTES, subscription.storageQuotaLimitBytes)
            .set(PADDLE_SUBSCRIPTION_ID, subscription.paddleSubscriptionId)
            .set(PADDLE_CUSTOMER_ID, subscription.paddleCustomerId)
            .set(CANCELLED_AT, subscription.cancelledAt)
            .set(TRIAL_START, subscription.trialStart)
            .set(TRIAL_END, subscription.trialEnd)
            .set(TRIAL_PLAN_TYPE, subscription.trialPlanType?.name)
            .set(PAUSED_AT, subscription.pausedAt)
            .set(RESUME_AT, subscription.resumeAt)
            .where(ID.eq(subscription.id))
            .execute()

        return findById(subscription.id!!)!!
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

    override fun findWithPendingPlanType(): List<Subscription> =
        dsl.select()
            .from(SUBSCRIPTIONS)
            .where(PENDING_PLAN_TYPE.isNotNull)
            .fetch()
            .map { it.toSubscription() }

    /**
     * 만료 처리 대상 트라이얼.
     *
     * status 가 TRIALING 인 것만 보면, 어떤 이유로든 status 가 덮여 쓰인 트라이얼은
     * 영원히 만료되지 않고 유료 플랜이 무기한 유지된다. 그래서 "결제 수단이 없는데
     * (paddleSubscriptionId IS NULL) 트라이얼 종료일이 지난" 건까지 함께 회수한다.
     * 정상적으로 유료 전환된 구독은 paddleSubscriptionId 가 있으므로 제외된다.
     */
    override fun findTrialExpired(now: LocalDateTime): List<Subscription> =
        dsl.select()
            .from(SUBSCRIPTIONS)
            .where(TRIAL_END.lessOrEqual(now))
            .and(
                STATUS_TEXT.eq(SubscriptionStatus.TRIALING.name)
                    .or(PADDLE_SUBSCRIPTION_ID.isNull.and(TRIAL_START.isNotNull)),
            )
            .fetch()
            .map { it.toSubscription() }

    override fun findPausedToResume(now: LocalDateTime): List<Subscription> =
        dsl.select()
            .from(SUBSCRIPTIONS)
            .where(STATUS_TEXT.eq(SubscriptionStatus.PAUSED.name))
            .and(RESUME_AT.lessOrEqual(now))
            .fetch()
            .map { it.toSubscription() }

    private fun Record.toSubscription(): Subscription {
        val billingCycleStr = get(BILLING_CYCLE)
        val planTypeStr = get(PLAN_TYPE) ?: "FREE"
        val statusStr = get(STATUS) ?: "FREE"
        val pendingPlanTypeStr = get(PENDING_PLAN_TYPE)

        return Subscription(
            id = get(ID),
            userId = get(USER_ID),
            planType = try { PlanType.valueOf(planTypeStr) } catch (_: Exception) { PlanType.FREE },
            status = try { SubscriptionStatus.valueOf(statusStr) } catch (_: Exception) { SubscriptionStatus.FREE },
            price = get(PRICE),
            billingCycle = billingCycleStr?.let { try { BillingCycle.valueOf(it) } catch (_: Exception) { BillingCycle.MONTHLY } } ?: BillingCycle.MONTHLY,
            currentPeriodStart = localDateTime(CURRENT_PERIOD_START),
            currentPeriodEnd = localDateTime(CURRENT_PERIOD_END),
            nextBillingDate = localDateTime(NEXT_BILLING_DATE),
            pendingPlanType = pendingPlanTypeStr?.let { try { PlanType.valueOf(it) } catch (_: Exception) { null } },
            storageQuotaLimitBytes = get(STORAGE_QUOTA_LIMIT_BYTES),
            paddleSubscriptionId = get(PADDLE_SUBSCRIPTION_ID),
            paddleCustomerId = get(PADDLE_CUSTOMER_ID),
            cancelledAt = localDateTime(CANCELLED_AT),
            trialStart = localDateTime(TRIAL_START),
            trialEnd = localDateTime(TRIAL_END),
            trialPlanType = get(TRIAL_PLAN_TYPE)?.let { try { PlanType.valueOf(it) } catch (_: Exception) { null } },
            pausedAt = localDateTime(PAUSED_AT),
            resumeAt = localDateTime(RESUME_AT),
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
    }
}
