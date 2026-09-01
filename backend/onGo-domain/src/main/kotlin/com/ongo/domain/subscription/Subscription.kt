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
    /** 기간 종료 후 함께 적용할 결제 주기. 다운그레이드와 주기 변경을 한 번에 예약할 때만 값이 있다. */
    val pendingBillingCycle: BillingCycle? = null,
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
) {
    /**
     * 이 구독을 ACTIVE 로 두기 전에 **채워져 있어야 하는** 청구창 값의 이름.
     * 빈 리스트면 그대로 활성화해도 된다.
     *
     * ## 왜 필요한가
     *
     * 기간이 비어 있는 ACTIVE 유료 구독은 **청구되지도 만료되지도 않는다.** SQL 의 NULL
     * 비교는 참이 아니라 UNKNOWN 이라 `findDueForBilling`(`next_billing_date <= now`) 과
     * `findTrialExpired`(`trial_end <= now`) 가 그 행을 영원히 건너뛴다. 그러면 결제 없이
     * 유료 권한이 무기한 유지되고, 그 손해는 되돌릴 수 없다.
     *
     * 그런 구독이 운영에서 실제로 발견됐다(BUSINESS, 기간 전부 NULL, 결제 0건). 상태를
     * ACTIVE 로 되돌리는 경로가 여럿이라(관리자 활성화, 일시정지 자동 재개) 판단을 한
     * 곳에만 두고 각 경로가 이것을 부른다 — 규칙이 흩어지면 한쪽만 고치게 된다.
     *
     * ## FREE 는 대상이 아니다
     *
     * 무료 구독에는 청구창이 없다. 정상적으로 만들어진 FREE 행부터 기간이 NULL 이므로
     * 여기에 걸면 무료 사용자의 상태 전환이 통째로 막힌다.
     */
    fun missingPaidBillingWindow(): List<String> {
        if (planType == PlanType.FREE) return emptyList()
        return buildList {
            if (currentPeriodStart == null) add("current_period_start")
            if (currentPeriodEnd == null) add("current_period_end")
            if (nextBillingDate == null) add("next_billing_date")
        }
    }
}
