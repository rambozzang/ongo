package com.ongo.domain.subscription

import com.ongo.common.enums.PlanType
import java.time.LocalDateTime

interface SubscriptionRepository {
    fun findByUserId(userId: Long): Subscription?
    fun save(subscription: Subscription): Subscription
    fun update(subscription: Subscription): Subscription
    fun findDueForBilling(now: LocalDateTime): List<Subscription>
    fun findPastDue(gracePeriodDays: Int): List<Subscription>
    fun findByPlanType(planType: PlanType): List<Subscription>
}
