package com.ongo.application.subscription

import com.ongo.application.paddle.PaddleGateway
import com.ongo.application.subscription.dto.*
import com.ongo.application.storage.StorageQuotaUseCase
import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.common.exception.BusinessException
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

@Service
class SubscriptionUseCase(
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository,
    private val videoRepository: VideoRepository,
    private val paddleGateway: PaddleGateway,
    /** 영상·에셋·게시 이미지·진행 중 예약을 합산하는 단일 저장공간 기준. */
    private val storageQuotaUseCase: StorageQuotaUseCase,
    /** 체험 시작 시 플랜 크레딧 권한을 같은 트랜잭션에서 적용한다. */
    private val creditService: com.ongo.application.credit.CreditService,
    /** 전환 퍼널 측정. 체험이 실제로 시작된 뒤에만 기록한다. */
    private val activityLogUseCase: com.ongo.application.activitylog.ActivityLogUseCase,
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
        val isBillingCycleUpgrade = subscription.billingCycle == BillingCycle.MONTHLY &&
            billingCycle == BillingCycle.YEARLY
        val isPaidCycleChange = targetPlan != PlanType.FREE &&
            isBillingCycleUpgrade &&
            billingCycle != subscription.billingCycle &&
            targetPlan.price >= subscription.planType.price
        val now = LocalDateTime.now()

        // 같은 상태를 다시 저장해 예약이 영원히 남거나 적용일이 밀리지 않게 한다.
        if (targetPlan == subscription.planType &&
            (billingCycle == subscription.billingCycle || targetPlan == PlanType.FREE)
        ) {
            return ChangePlanResponse(subscription.toResponse(), null, subscription.currentPeriodEnd ?: now)
        }

        // Paddle 구독이 있는 경우 Paddle API로 변경 처리
        if (subscription.paddleSubscriptionId != null) {
            val priceId = paddleGateway.getPriceIdForPlan(targetPlan.name, billingCycle.name)
                ?: throw IllegalArgumentException("해당 플랜의 Paddle 가격 ID를 찾을 수 없습니다")

            val prorationMode = if (isUpgrade) "prorated_immediately" else "prorated_next_billing_period"
            paddleGateway.updateSubscription(subscription.paddleSubscriptionId!!, priceId, prorationMode)

            // Paddle이 웹훅으로 DB를 동기화하므로 여기선 응답만 반환
            val effectiveDate = if (isUpgrade) now else (subscription.currentPeriodEnd ?: now)
            return ChangePlanResponse(subscription.toResponse(), null, effectiveDate)
        }

        /*
         * 같은 플랜의 월간↔연간 전환과 상향을 동반한 주기 전환은 새 금액을 즉시 결제해야
         * 한다. 이 API가 예약만 세우면 결제 없이 연간 권한·기간을 얻거나, 결제 화면과
         * 서버의 의도가 서로 달라진다. 하위 플랜의 주기 변경만은 이미 결제한 기간을
         * 존중해 기간 종료 시 플랜과 주기를 함께 바꾸도록 아래 예약 경로에서 처리한다.
         */
        if (isPaidCycleChange) {
            throw BusinessException(
                "PAYMENT_REQUIRED",
                "결제 주기를 변경하려면 구독 화면에서 결제를 진행해 주세요.",
            )
        }

        /*
         * 여기부터는 **결제 수단이 연결되지 않은 구독**이다(paddleSubscriptionId 가 없다).
         * 이 경로로 유료 플랜을 올리는 것은 전부 거절한다.
         *
         * ## 왜 성공 응답이 아니라 예외인가
         *
         * 예전에는 TRIALING·FREE 두 경우를 "아무것도 하지 않고 성공 응답"으로 돌려보냈다.
         * 그러면 호출자는 요청이 받아들여진 줄 알고, 프런트가 체크아웃을 여는 분기를
         * 놓치면 사용자는 플랜이 바뀐 줄 안다. 거절은 거절이라고 말해야 한다.
         *
         * ## 왜 유료 → 상위 유료도 막는가
         *
         * 그 분기는 아래에서 planType·price 를 **즉시 갱신하면서 결제를 만들지 않았다.**
         * proratedAmount 를 계산해 응답에 담기만 했을 뿐 청구가 없어서, STARTER 고객이
         * 이 API 한 번으로 BUSINESS 를 얻었다. 일할 계산 주석이 있다는 것은 원래 청구가
         * 의도였다는 뜻이고, 그 청구가 빠진 채 적용만 남아 있었다.
         *
         * 상향은 반드시 체크아웃(PortOne)을 거쳐야 한다. 결제가 확정되면
         * `PortOnePaymentService.completeSubscription` 이 플랜을 바꾼다 — 플랜을 바꾸는
         * 곳은 결제가 끝난 그 한 곳이어야 한다.
         *
         * 다운그레이드는 막지 않는다. 돈을 더 받지 않으므로 결제가 필요 없고, 플랜과
         * 결제 주기를 기간 종료 후 함께 적용되도록 예약한다.
         */
        if (isUpgrade || (subscription.planType == PlanType.FREE && targetPlan != PlanType.FREE)) {
            throw BusinessException(
                "PAYMENT_REQUIRED",
                "플랜을 올리려면 결제가 필요합니다. 구독 화면에서 결제를 진행해 주세요.",
            )
        }

        /*
         * 체험 중에는 다운그레이드도 막는다.
         *
         * 체험은 planType 이 유료인데 결제가 없다. 여기서 FREE 로 내리면 아래 예약 분기가
         * pendingPlanType 을 세우는데, 체험 만료 처리(BillingScheduler)가 이미 FREE 전환을
         * 맡고 있어 두 경로가 같은 구독을 각각 건드리게 된다. 체험 해지는 취소 API 의 몫이다.
         */
        if (subscription.status == SubscriptionStatus.TRIALING) {
            throw BusinessException(
                "PAYMENT_REQUIRED",
                "체험 중에는 플랜을 변경할 수 없습니다. 구독 화면에서 결제하거나 체험을 해지해 주세요.",
            )
        }

        /*
         * 여기까지 왔다면 결제가 필요 없는 변경, 즉 다운그레이드(또는 동일가 플랜)다.
         *
         * 상향 분기는 위에서 예외로 끝나므로 여기 없다. 결제 없이 planType·price 를 갱신하는
         * 코드가 남아 있으면 위 가드가 나중에 느슨해질 때 그대로 되살아난다. 상향은 결제가
         * 확정된 뒤 `PortOnePaymentService.completeSubscription` 한 곳에서만 적용한다.
         *
         * 즉시 적용하지 않고 예약한다. 이미 받은 이번 주기 요금만큼은 쓰게 두는 것이 맞고,
         * 실제 전환은 BillingScheduler 의 pendingPlanType 처리가 기간 종료 후에 한다.
         */
        val effectiveDate = subscription.currentPeriodEnd ?: now
        val cycleChanged = billingCycle != subscription.billingCycle
        val updated = subscription.copy(
            pendingPlanType = targetPlan,
            pendingBillingCycle = billingCycle.takeIf { cycleChanged && targetPlan != PlanType.FREE },
            updatedAt = now,
        )
        subscriptionRepository.update(updated)
        return ChangePlanResponse(updated.toResponse(), null, effectiveDate)
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
            cancelledAt = LocalDateTime.now(),
            // 해지한 구독의 하향 예약이 나중에 다시 활성화되는 경로를 남기지 않는다.
            pendingPlanType = null,
            pendingBillingCycle = null,
        )
        subscriptionRepository.update(updated)
        return updated.toResponse()
    }

    @Transactional
    fun startTrial(userId: Long, targetPlan: String): SubscriptionResponse {
        val plan = safeValueOfOrThrow<PlanType>(targetPlan)
        val subscription = subscriptionRepository.findByUserId(userId)
            ?: throw NotFoundException("구독", userId)
        /*
         * 제품 CTA(SubscriptionView)는 STARTER 만 보낸다. 다른 플랜을 받으면 사용자가
         * API 를 직접 불러 BUSINESS 체험을 여는 경로가 열리고, 그 크레딧(1,000)은
         * 되돌릴 방법이 없다 — 체험은 1회성이라 두 번째 기회가 없다.
         */
        if (plan != PlanType.STARTER)
            throw IllegalStateException("체험은 Starter 플랜만 가능합니다")
        if (subscription.planType != PlanType.FREE)
            throw IllegalStateException("무료 플랜 사용자만 트라이얼 시작 가능")
        if (subscription.trialStart != null)
            throw IllegalStateException("이미 트라이얼을 사용한 적이 있습니다")
        val now = LocalDateTime.now()
        val trialEnd = now.plusDays(7)
        val updated = subscription.copy(
            status = SubscriptionStatus.TRIALING,
            trialStart = now,
            trialEnd = trialEnd,
            trialPlanType = plan,
            planType = plan,
            price = 0,
            updatedAt = now,
        )
        subscriptionRepository.update(updated)
        userRepository.update(userRepository.findById(userId)!!.copy(planType = plan))
        /*
         * 같은 @Transactional 안이라 구독·사용자 갱신과 함께 커밋되거나 함께 롤백된다.
         * 나눠 두면 "구독은 TRIALING 인데 크레딧은 30" 인 상태가 남고, 사용자는 체험을
         * 시작했는데 아무것도 못 하게 된다.
         */
        creditService.applyPlanEntitlement(userId, plan, reason = "TRIAL_START")
        /*
         * 구독·사용자·크레딧이 모두 반영된 **뒤에** 기록한다. 앞에 두면 뒤에서 실패해도
         * 체험을 시작한 것처럼 남는다.
         *
         * 일반(트랜잭션 결속) 기록이다. 이 트랜잭션이 롤백되면 체험도 없으므로 흔적도
         * 함께 사라져야 한다.
         */
        activityLogUseCase.logActivity(
            userId = userId,
            action = com.ongo.application.activitylog.ActivityLogActions.SUBSCRIPTION_TRIAL_STARTED,
            entityType = com.ongo.application.activitylog.ActivityLogActions.ENTITY_SUBSCRIPTION,
        )
        return updated.toResponse()
    }

    @Transactional
    fun pauseSubscription(userId: Long): SubscriptionResponse {
        val sub = subscriptionRepository.findByUserId(userId)
            ?: throw NotFoundException("구독", userId)
        if (sub.status != SubscriptionStatus.ACTIVE)
            throw IllegalStateException("활성 구독만 일시정지 가능")
        if (sub.paddleSubscriptionId != null) {
            paddleGateway.pauseSubscription(sub.paddleSubscriptionId!!)
        }
        val now = LocalDateTime.now()
        val updated = sub.copy(
            status = SubscriptionStatus.PAUSED,
            pausedAt = now,
            resumeAt = now.plusDays(30),
            updatedAt = now,
        )
        subscriptionRepository.update(updated)
        return updated.toResponse()
    }

    @Transactional
    fun resumeSubscription(userId: Long): SubscriptionResponse {
        val sub = subscriptionRepository.findByUserId(userId)
            ?: throw NotFoundException("구독", userId)
        if (sub.status != SubscriptionStatus.PAUSED)
            throw IllegalStateException("일시정지 상태만 재개 가능")
        if (sub.paddleSubscriptionId != null) {
            paddleGateway.resumeSubscription(sub.paddleSubscriptionId!!)
        }
        val now = LocalDateTime.now()
        val updated = sub.copy(
            status = SubscriptionStatus.ACTIVE,
            pausedAt = null,
            resumeAt = null,
            updatedAt = now,
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
                yearlyPrice = plan.yearlyPrice,
                features = plan.toFeatures(),
                recommended = plan == PlanType.PRO
            )
        }
        return PlanComparisonResponse(plans = plans, currentPlan = user.planType)
    }

    fun getUsage(userId: Long): UsageResponse {
        val currentMonth = YearMonth.now()
        val uploadsThisMonth = videoRepository.countByUserIdAndMonth(userId, currentMonth).toInt()

        // 업로드 화면의 쿼터 검사와 같은 기준을 사용한다. 영상만 더하면 에셋·게시 이미지가
        // 빠져 실제보다 적게 보여지고, 사용자가 플랜 한도를 우회할 수 있다.
        val storageUsedBytes = storageQuotaUseCase.getCurrentUsage(userId)
        val storageLimitBytes = storageQuotaUseCase.getEffectiveLimit(userId)
        val storageUsedMb = storageUsedBytes / (1024 * 1024)

        return UsageResponse(
            uploadsThisMonth = uploadsThisMonth,
            storageUsedMb = storageUsedMb,
            storageLimitBytes = storageLimitBytes,
        )
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

    private fun Subscription.toResponse(): SubscriptionResponse = SubscriptionResponse(
        planType = planType,
        status = status,
        price = price,
        billingCycle = billingCycle,
        currentPeriodEnd = currentPeriodEnd,
        nextBillingDate = nextBillingDate,
        features = planType.toFeatures(),
        trialEnd = trialEnd,
        pausedAt = pausedAt,
        resumeAt = resumeAt,
        pendingPlanType = pendingPlanType,
        pendingBillingCycle = pendingBillingCycle,
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
