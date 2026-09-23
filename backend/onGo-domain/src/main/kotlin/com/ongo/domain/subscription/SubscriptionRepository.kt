package com.ongo.domain.subscription

import com.ongo.common.enums.PlanType
import java.time.LocalDateTime

interface SubscriptionRepository {
    fun findByUserId(userId: Long): Subscription?

    /**
     * 구독 id 로 조회한다.
     *
     * 갱신 원장은 userId 가 아니라 subscriptionId 를 갖는다. 운영자 재확인은 **저장된
     * 스냅샷이 아니라 지금 상태**를 봐야 하므로 그때마다 다시 읽는다.
     */
    fun findById(id: Long): Subscription?
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

    /**
     * **자동 갱신이 꺼져 있을 때** 결제 기간이 끝난 유료 ACTIVE 구독. Free 로 내려야 할 대상이다.
     *
     * 경계는 `current_period_end ?: next_billing_date` 다(하향 예약과 같은 규칙). **둘 다 비어
     * 있는 행은 포함하지 않는다** — 그 행은 결제로 만들어진 기간이 아니라서 사람이 먼저 봐야 한다
     * (`deploy/audit/payment-consistency.sql` 의 PAID_WINDOW_MISSING).
     * 레거시 Paddle 구독은 Paddle 이 기간을 관리하므로 제외한다. 하향 예약이 남은 행도 제외한다 —
     * 같은 실행에서 하향 적용 단계가 먼저 처리한다.
     */
    fun findActiveExpiredWithoutRenewal(now: LocalDateTime): List<Subscription>

    /** [findActiveExpiredWithoutRenewal] 과 같은 대상 중 경계가 `[from, to)` 에 드는 구독. 만료 예고용. */
    fun findActiveEndingBetween(from: LocalDateTime, to: LocalDateTime): List<Subscription>
}
