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
    /**
     * PortOne 빌링키(AES-256 암호화). 없으면 정기 청구를 시도할 수 없다.
     *
     * 응답 DTO·로그에 절대 싣지 않는다 — 이 값 하나로 고객에게 반복 청구가 가능해
     * 유출 시 피해가 액세스 토큰보다 크다.
     */
    val billingKeyEncrypted: String? = null,
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
