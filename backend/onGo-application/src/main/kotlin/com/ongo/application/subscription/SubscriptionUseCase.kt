package com.ongo.application.subscription

import com.ongo.application.subscription.dto.*
import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.common.exception.NotFoundException
import com.ongo.common.util.safeValueOfOrThrow
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
class SubscriptionUseCase(
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository
) {

    fun getCurrentSubscription(userId: Long): SubscriptionResponse {
        val subscription = subscriptionRepository.findByUserId(userId)
            ?: throw NotFoundException("구독", userId)
        return subscription.toResponse()
    }

    @Transactional
    fun changePlan(userId: Long, request: ChangePlanRequest): ChangePlanResponse {
        val targetPlan = safeValueOfOrThrow<PlanType>(request.targetPlan)
        val billingCycle = safeValueOfOrThrow<BillingCycle>(request.billingCycle)
        val subscription = subscriptionRepository.findByUserId(userId)
            ?: throw NotFoundException("구독", userId)
        val user = userRepository.findById(userId)!!

        val isUpgrade = targetPlan.price > subscription.planType.price
        val now = LocalDateTime.now()

        if (isUpgrade) {
            // 업그레이드: 즉시 적용, 일할 계산 차액
            val remainingDays = subscription.currentPeriodEnd?.let {
                ChronoUnit.DAYS.between(now, it).toInt()
            } ?: 0
            val proratedAmount = calculateProration(subscription.planType, targetPlan, remainingDays)

            val updated = subscription.copy(
                planType = targetPlan,
                price = targetPlan.price,
                billingCycle = billingCycle,
                status = SubscriptionStatus.ACTIVE,
                updatedAt = now
            )
            subscriptionRepository.update(updated)
            userRepository.update(user.copy(planType = targetPlan))

            return ChangePlanResponse(updated.toResponse(), proratedAmount, now)
        } else {
            // 다운그레이드: 현재 기간 종료 후 적용
            val effectiveDate = subscription.currentPeriodEnd ?: now
            // 다운그레이드 예약: pendingPlanType에 대상 플랜 저장
            val updated = subscription.copy(
                pendingPlanType = targetPlan,
                updatedAt = now
            )
            subscriptionRepository.update(updated)
            return ChangePlanResponse(updated.toResponse(), null, effectiveDate)
        }
    }

    @Transactional
    fun cancelSubscription(userId: Long): SubscriptionResponse {
        val subscription = subscriptionRepository.findByUserId(userId)
            ?: throw NotFoundException("구독", userId)
        val updated = subscription.copy(
            status = SubscriptionStatus.CANCELLED,
            cancelledAt = LocalDateTime.now()
        )
        subscriptionRepository.update(updated)
        return updated.toResponse()
    }

    fun getPlans(userId: Long): PlanComparisonResponse {
        val user = userRepository.findById(userId)!!
        val plans = PlanType.entries.map { plan ->
            PlanInfo(
                planType = plan,
                price = plan.price,
                features = plan.toFeatures(),
                recommended = plan == PlanType.PRO
            )
        }
        return PlanComparisonResponse(plans = plans, currentPlan = user.planType)
    }

    fun getUsage(userId: Long): UsageResponse {
        // TODO: 실제 업로드 수 및 스토리지 사용량 계산
        return UsageResponse(uploadsThisMonth = 0, storageUsedMb = 0)
    }

    fun initializeSubscription(userId: Long): Subscription {
        val subscription = Subscription(
            userId = userId,
            planType = PlanType.FREE,
            status = SubscriptionStatus.FREE,
            price = 0,
            billingCycle = BillingCycle.MONTHLY,
            currentPeriodStart = LocalDateTime.now(),
            currentPeriodEnd = null
        )
        return subscriptionRepository.save(subscription)
    }

    private fun calculateProration(current: PlanType, target: PlanType, remainingDays: Int): Int {
        val dailyDiff = (target.price - current.price) / 30.0
        return (dailyDiff * remainingDays).toInt().coerceAtLeast(0)
    }

    private fun Subscription.toResponse(): SubscriptionResponse = SubscriptionResponse(
        planType = planType,
        status = status,
        price = price,
        billingCycle = billingCycle,
        currentPeriodEnd = currentPeriodEnd,
        nextBillingDate = nextBillingDate,
        features = planType.toFeatures()
    )

    private fun PlanType.toFeatures(): PlanFeatures = PlanFeatures(
        maxPlatforms = maxPlatforms,
        monthlyUploads = monthlyUploads,
        scheduleDays = scheduleDays,
        analyticsDays = analyticsDays,
        storageGB = storageGB,
        freeCredits = freeCredits,
        maxTeamMembers = maxTeamMembers
    )
}
