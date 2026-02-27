package com.ongo.application.subscription

import com.ongo.application.paddle.PaddleGateway
import com.ongo.application.subscription.dto.*
import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.common.exception.NotFoundException
import com.ongo.common.util.safeValueOfOrThrow
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.UserRepository
import com.ongo.domain.video.VideoRepository
import java.time.YearMonth
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
class SubscriptionUseCase(
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository,
    private val videoRepository: VideoRepository,
    private val paddleGateway: PaddleGateway,
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
        val user = userRepository.findById(userId)
            ?: throw NotFoundException("사용자", userId)

        val isUpgrade = targetPlan.price > subscription.planType.price
        val now = LocalDateTime.now()

        // Paddle 구독이 있는 경우 Paddle API로 변경 처리
        if (subscription.paddleSubscriptionId != null) {
            val priceId = paddleGateway.getPriceIdForPlan(targetPlan.name)
                ?: throw IllegalArgumentException("해당 플랜의 Paddle 가격 ID를 찾을 수 없습니다")

            val prorationMode = if (isUpgrade) "prorated_immediately" else "prorated_next_billing_period"
            paddleGateway.updateSubscription(subscription.paddleSubscriptionId!!, priceId, prorationMode)

            // Paddle이 웹훅으로 DB를 동기화하므로 여기선 응답만 반환
            val effectiveDate = if (isUpgrade) now else (subscription.currentPeriodEnd ?: now)
            return ChangePlanResponse(subscription.toResponse(), null, effectiveDate)
        }

        // FREE → 유료 전환 (Paddle 체크아웃 필요 → 프론트엔드에서 체크아웃 오버레이를 열어야 함)
        if (subscription.planType == PlanType.FREE && targetPlan != PlanType.FREE) {
            return ChangePlanResponse(subscription.toResponse(), null, now)
        }

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

        // Paddle 구독 취소 (실제 취소는 Paddle 웹훅에서 처리)
        if (subscription.paddleSubscriptionId != null) {
            paddleGateway.cancelSubscription(subscription.paddleSubscriptionId!!, "next_billing_period")
            return subscription.toResponse()
        }

        val updated = subscription.copy(
            status = SubscriptionStatus.CANCELLED,
            cancelledAt = LocalDateTime.now()
        )
        subscriptionRepository.update(updated)
        return updated.toResponse()
    }

    fun getPlans(userId: Long): PlanComparisonResponse {
        val user = userRepository.findById(userId)
            ?: throw NotFoundException("사용자", userId)
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
        val currentMonth = YearMonth.now()
        val uploadsThisMonth = videoRepository.countByUserIdAndMonth(userId, currentMonth).toInt()

        // Calculate storage: sum all video file sizes
        val videos = videoRepository.findByUserId(userId, page = 0, size = 10000)
        val storageUsedBytes = videos.sumOf { it.fileSizeBytes ?: 0L }
        val storageUsedMb = storageUsedBytes / (1024 * 1024)

        return UsageResponse(uploadsThisMonth = uploadsThisMonth, storageUsedMb = storageUsedMb)
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
