package com.ongo.domain.subscription

import com.ongo.common.enums.PlanType
import java.time.LocalDateTime

interface SubscriptionRepository {
    fun findByUserId(userId: Long): Subscription?
    fun findByPaddleSubscriptionId(paddleSubscriptionId: String): Subscription?
    fun save(subscription: Subscription): Subscription
    fun update(subscription: Subscription): Subscription
    fun findDueForBilling(now: LocalDateTime): List<Subscription>
    fun findPastDue(gracePeriodDays: Int): List<Subscription>
    fun findByPlanType(planType: PlanType): List<Subscription>
    fun findWithPendingPlanType(): List<Subscription>
    fun findTrialExpired(now: LocalDateTime): List<Subscription>

    /** 취소되었고 결제 기간까지 끝난 구독. Free 로 내려야 할 대상이다. */
    fun findCancelledExpired(now: LocalDateTime): List<Subscription>
    fun findPausedToResume(now: LocalDateTime): List<Subscription>
}
