package com.ongo.api.subscription.dto

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
    val features: PlanFeatures
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
    @field:jakarta.validation.constraints.NotBlank(message = "대상 플랜은 필수입니다")
    @field:jakarta.validation.constraints.Size(max = 20, message = "플랜 이름은 최대 20자까지 입력할 수 있습니다")
    val targetPlan: String,
    @field:jakarta.validation.constraints.NotBlank(message = "빌링 주기는 필수입니다")
    @field:jakarta.validation.constraints.Size(max = 20, message = "빌링 주기는 최대 20자까지 입력할 수 있습니다")
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
    val features: PlanFeatures,
    val recommended: Boolean = false
)

data class PlanComparisonResponse(
    val plans: List<PlanInfo>,
    val currentPlan: PlanType
)
