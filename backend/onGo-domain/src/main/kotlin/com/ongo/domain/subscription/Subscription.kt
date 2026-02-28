package com.ongo.domain.subscription

import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import java.time.LocalDateTime

data class Subscription(
    val id: Long? = null,
    val userId: Long,
    val planType: PlanType,
    val status: SubscriptionStatus = SubscriptionStatus.FREE,
    val price: Int = 0,
    val billingCycle: BillingCycle = BillingCycle.MONTHLY,
    val currentPeriodStart: LocalDateTime? = null,
    val currentPeriodEnd: LocalDateTime? = null,
    val nextBillingDate: LocalDateTime? = null,
    val pendingPlanType: PlanType? = null,
    val storageQuotaLimitBytes: Long? = null,
    val paddleSubscriptionId: String? = null,
    val paddleCustomerId: String? = null,
    val cancelledAt: LocalDateTime? = null,
    val trialStart: LocalDateTime? = null,
    val trialEnd: LocalDateTime? = null,
    val trialPlanType: PlanType? = null,
    val pausedAt: LocalDateTime? = null,
    val resumeAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
