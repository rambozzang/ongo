package com.ongo.application.subscription.dto

import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import java.time.LocalDateTime

data class SubscriptionResponse(
    val planType: PlanType,
    val status: SubscriptionStatus,
    val price: Int,
    val billingCycle: BillingCycle,
    val currentPeriodEnd: LocalDateTime?,
    val nextBillingDate: LocalDateTime?,
    val features: PlanFeatures,
    val trialEnd: LocalDateTime? = null,
    val pausedAt: LocalDateTime? = null,
    val resumeAt: LocalDateTime? = null,
)

data class PlanFeatures(
    val maxPlatforms: Int,
    val monthlyUploads: Int,
    val scheduleDays: Int,
    val analyticsDays: Int,
    val storageGB: Int,
    val freeCredits: Int,
    val maxTeamMembers: Int
)

data class ChangePlanRequest(
    val targetPlan: String,
    val billingCycle: String = "MONTHLY"
)

data class ChangePlanResponse(
    val subscription: SubscriptionResponse,
    val proratedAmount: Int?,
    val effectiveDate: LocalDateTime
)

data class PlanInfo(
    val planType: PlanType,
    val price: Int,
    val yearlyPrice: Int,
    val features: PlanFeatures,
    val recommended: Boolean = false
)

data class PlanComparisonResponse(
    val plans: List<PlanInfo>,
    val currentPlan: PlanType
)

data class StartTrialRequest(
    val targetPlan: String,
)

data class UsageResponse(
    val uploadsThisMonth: Int,
    val storageUsedMb: Long,
)
