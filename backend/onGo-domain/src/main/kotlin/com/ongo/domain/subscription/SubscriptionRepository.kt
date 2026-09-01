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
}
