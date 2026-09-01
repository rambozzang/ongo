package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.infrastructure.persistence.jooq.Fields.BILLING_CYCLE
import com.ongo.infrastructure.persistence.jooq.Fields.CANCELLED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.PADDLE_CUSTOMER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.PADDLE_SUBSCRIPTION_ID
import com.ongo.infrastructure.persistence.jooq.Fields.PAUSED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.BILLING_KEY_ENCRYPTED
import com.ongo.infrastructure.persistence.jooq.Fields.PENDING_BILLING_CYCLE
import com.ongo.infrastructure.persistence.jooq.Fields.PENDING_PLAN_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.CURRENT_PERIOD_END
import com.ongo.infrastructure.persistence.jooq.Fields.CURRENT_PERIOD_START
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.NEXT_BILLING_DATE
import com.ongo.infrastructure.persistence.jooq.Fields.PLAN_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.PLAN_TYPE_TEXT
import com.ongo.infrastructure.persistence.jooq.Fields.PRICE
import com.ongo.infrastructure.persistence.jooq.Fields.RESUME_AT
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS_TEXT
import com.ongo.infrastructure.persistence.jooq.Fields.STORAGE_QUOTA_LIMIT_BYTES
import com.ongo.infrastructure.persistence.jooq.Fields.TRIAL_END
import com.ongo.infrastructure.persistence.jooq.Fields.TRIAL_PLAN_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.TRIAL_START
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.enumValue
import com.ongo.infrastructure.persistence.jooq.Tables.SUBSCRIPTIONS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class SubscriptionJooqRepository(
    private val dsl: DSLContext,
) : SubscriptionRepository {

    override fun findByUserId(userId: Long): Subscription? =
        dsl.select()
            .from(SUBSCRIPTIONS)
            .where(USER_ID.eq(userId))
            .fetchOne()
            ?.toSubscription()

    override fun findByPaddleSubscriptionId(paddleSubscriptionId: String): Subscription? =
        dsl.select()
            .from(SUBSCRIPTIONS)
            .where(PADDLE_SUBSCRIPTION_ID.eq(paddleSubscriptionId))
            .fetchOne()
            ?.toSubscription()

    override fun findById(id: Long): Subscription? =
        dsl.select()
            .from(SUBSCRIPTIONS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toSubscription()

    override fun save(subscription: Subscription): Subscription {
        val id = dsl.insertInto(SUBSCRIPTIONS)
            .set(USER_ID, subscription.userId)
            .set(PLAN_TYPE, enumValue("plan_type", subscription.planType.name))
            .set(STATUS, enumValue("subscription_status", subscription.status.name))
            .set(PRICE, subscription.price)
            .set(BILLING_CYCLE, enumValue("billing_cycle", subscription.billingCycle.name))
            .set(CURRENT_PERIOD_START, subscription.currentPeriodStart)
            .set(CURRENT_PERIOD_END, subscription.currentPeriodEnd)
            .set(NEXT_BILLING_DATE, subscription.nextBillingDate)
            .set(PENDING_PLAN_TYPE, subscription.pendingPlanType?.name?.let { enumValue("plan_type", it) })
            .set(PENDING_BILLING_CYCLE, subscription.pendingBillingCycle?.name?.let { enumValue("billing_cycle", it) })
            .set(BILLING_KEY_ENCRYPTED, subscription.billingKeyEncrypted)
            .set(STORAGE_QUOTA_LIMIT_BYTES, subscription.storageQuotaLimitBytes)
            .set(PADDLE_SUBSCRIPTION_ID, subscription.paddleSubscriptionId)
            .set(PADDLE_CUSTOMER_ID, subscription.paddleCustomerId)
            .set(CANCELLED_AT, subscription.cancelledAt)
            .set(TRIAL_START, subscription.trialStart)
            .set(TRIAL_END, subscription.trialEnd)
            .set(TRIAL_PLAN_TYPE, subscription.trialPlanType?.name?.let { enumValue("plan_type", it) })
            .set(PAUSED_AT, subscription.pausedAt)
            .set(RESUME_AT, subscription.resumeAt)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(subscription: Subscription): Subscription {
        dsl.update(SUBSCRIPTIONS)
            .set(PLAN_TYPE, enumValue("plan_type", subscription.planType.name))
            .set(STATUS, enumValue("subscription_status", subscription.status.name))
            .set(PRICE, subscription.price)
            .set(BILLING_CYCLE, enumValue("billing_cycle", subscription.billingCycle.name))
            .set(CURRENT_PERIOD_START, subscription.currentPeriodStart)
            .set(CURRENT_PERIOD_END, subscription.currentPeriodEnd)
            .set(NEXT_BILLING_DATE, subscription.nextBillingDate)
            .set(PENDING_PLAN_TYPE, subscription.pendingPlanType?.name?.let { enumValue("plan_type", it) })
            .set(PENDING_BILLING_CYCLE, subscription.pendingBillingCycle?.name?.let { enumValue("billing_cycle", it) })
            .set(BILLING_KEY_ENCRYPTED, subscription.billingKeyEncrypted)
            .set(STORAGE_QUOTA_LIMIT_BYTES, subscription.storageQuotaLimitBytes)
            .set(PADDLE_SUBSCRIPTION_ID, subscription.paddleSubscriptionId)
            .set(PADDLE_CUSTOMER_ID, subscription.paddleCustomerId)
            .set(CANCELLED_AT, subscription.cancelledAt)
            .set(TRIAL_START, subscription.trialStart)
            .set(TRIAL_END, subscription.trialEnd)
            .set(TRIAL_PLAN_TYPE, subscription.trialPlanType?.name?.let { enumValue("plan_type", it) })
            .set(PAUSED_AT, subscription.pausedAt)
            .set(RESUME_AT, subscription.resumeAt)
            .where(ID.eq(subscription.id))
            .execute()

        return findById(subscription.id!!)!!
    }

    /**
     * PortOne 자동 갱신 대상.
     *
     * ## Paddle 구독은 여기 들어오면 안 된다
     *
     * 이 목록은 [com.ongo.application.subscription.SubscriptionRenewalService] 가 **PortOne
     * 빌링키로 청구**하는 대상이다. 그런데 Paddle 로 결제한 레거시 구독도 `status=ACTIVE`
     * 이고 `next_billing_date` 가 채워져 있어(`PaddleWebhookService.handleSubscriptionCreated`
     * 가 Paddle 의 `next_billed_at` 을 그대로 저장한다) 조건에 그대로 걸린다.
     *
     * 걸리면 어느 쪽으로 가든 고객이 손해를 본다.
     *
     *  - 빌링키가 없으면 청구가 실패해 PAST_DUE 가 되고, 7일 뒤 Free 로 강등된다.
     *    **Paddle 에서는 정상 결제 중인데 우리 쪽에서만 권한을 뺏는다.**
     *  - 빌링키가 있으면 Paddle 과 PortOne 이 같은 주기를 각각 청구한다 — **이중 청구**다.
     *
     * 자동 갱신은 `subscription.renewal.enabled` 로 꺼져 있지만, 켜는 순간 이 경로가 열린다.
     * 기능 토글은 배포 시점의 선택일 뿐이라 데이터 조건으로 막는다. 서비스 진입부에도 같은
     * 판정이 있다(`SubscriptionRenewalService.renew`) — 이 쿼리를 지나지 않는 다른 호출자가
     * 생겨도 청구까지 가지 않게 하기 위한 이중 방어다.
     *
     * **Paddle 갱신을 여기서 대신 처리하지 않는다.** 그것은 Paddle 웹훅의 몫이며, 이 조건은
     * "PortOne 이 건드리지 않는다"만 말한다.
     */
    override fun findDueForBilling(now: LocalDateTime): List<Subscription> =
        dsl.select()
            .from(SUBSCRIPTIONS)
            .where(STATUS_TEXT.eq(SubscriptionStatus.ACTIVE.name))
            .and(NEXT_BILLING_DATE.lessOrEqual(now))
            // 하향 예약은 기간 경계에서 먼저 적용한 뒤에만 갱신 대상으로 삼는다.
            // 동시 요청으로 예약이 새로 생긴 경우에도 옛 플랜으로 잘못 청구하지 않는다.
            .and(PENDING_PLAN_TYPE.isNull)
            // 예약 플랜과 주기는 항상 함께 기록되어야 한다. 주기만 남은 비정상 행도
            // 결제 대상에서 제외해, 현재 플랜/주기로 잘못 갱신하는 일을 막는다.
            .and(PENDING_BILLING_CYCLE.isNull)
            // 레거시 Paddle 구독 제외. 근거는 위 KDoc 에 있다.
            .and(PADDLE_SUBSCRIPTION_ID.isNull)
            .orderBy(NEXT_BILLING_DATE.asc())
            .fetch()
            .map { it.toSubscription() }

    override fun findPastDue(gracePeriodDays: Int): List<Subscription> {
        val cutoff = LocalDateTime.now().minusDays(gracePeriodDays.toLong())

        return dsl.select()
            .from(SUBSCRIPTIONS)
            .where(STATUS_TEXT.eq(SubscriptionStatus.PAST_DUE.name))
            .and(NEXT_BILLING_DATE.lessThan(cutoff))
            .fetch()
            .map { it.toSubscription() }
    }

    /**
     * 해당 요금제의 살아 있는 구독. 유료 스케줄러 작업(`CommentSyncScheduler`,
     * `WeeklyDigestScheduler`)이 대상을 고르는 데 쓴다.
     *
     * ## 유료 플랜은 **정상 청구창**만 돌려준다
     *
     * `current_period_start`·`current_period_end`·`next_billing_date` 가 모두 채워진
     * 행만 유료로 인정한다. 셋 중 하나라도 NULL 인 유료 구독은 **청구되지도 만료되지도
     * 않는다** — SQL 의 NULL 비교는 참이 아니라 UNKNOWN 이라
     * [findDueForBilling] (`next_billing_date <= now`) 과 [findTrialExpired]
     * (`trial_end <= now`) 가 그 행을 영원히 건너뛰기 때문이다. [findCancelledExpired] 가
     * 취소 구독에 대해 같은 문제를 다루는 이유와 정확히 같다.
     *
     * 그런 행이 여기에 걸리면 결과가 최악으로 어긋난다. **돈은 한 번도 걷히지 않는데
     * 유료 작업은 매 주기 나간다** — 댓글 동기화가 돌고, 주간 다이제스트가 AI 크레딧을
     * 태운다. 운영에서 실제로 그런 구독이 발견됐다(BUSINESS, 기간 전부 NULL, 결제 0건).
     *
     * 기간이 없는 유료 구독은 스스로 낫지 않으므로 **여기서 빼는 것으로 피해만 멈춘다.**
     * 그 행 자체는 남아 있고 운영자가 확인해야 한다 — 조용히 지우면 무엇이 잘못됐는지도
     * 함께 사라진다.
     *
     * ## FREE 는 종전 그대로다
     *
     * 무료 구독에는 청구창이 없다. `initializeNewUser` 가 만드는 정상 FREE 행부터 기간이
     * NULL 이므로, 같은 조건을 걸면 무료 사용자가 통째로 조회에서 사라진다.
     */
    override fun findByPlanType(planType: PlanType): List<Subscription> {
        val alive = PLAN_TYPE_TEXT.eq(planType.name)
            .and(STATUS_TEXT.`in`(SubscriptionStatus.ACTIVE.name, SubscriptionStatus.FREE.name))
        val condition = if (planType == PlanType.FREE) {
            alive
        } else {
            alive
                .and(CURRENT_PERIOD_START.isNotNull)
                .and(CURRENT_PERIOD_END.isNotNull)
                .and(NEXT_BILLING_DATE.isNotNull)
        }

        return dsl.select()
            .from(SUBSCRIPTIONS)
            .where(condition)
            .fetch()
            .map { it.toSubscription() }
    }

    override fun findWithPendingPlanType(): List<Subscription> =
        dsl.select()
            .from(SUBSCRIPTIONS)
            // 정상 경로에서는 두 예약 값이 함께 기록되지만, 한쪽만 남은 행도
            // 스케줄러가 감지해 경고할 수 있도록 둘 중 하나라도 조회한다.
            .where(PENDING_PLAN_TYPE.isNotNull.or(PENDING_BILLING_CYCLE.isNotNull))
            .fetch()
            .map { it.toSubscription() }

    /**
     * 취소되었고 **보호할 결제 기간이 남아 있지 않은** 구독.
     *
     * 예전에는 스케줄러가 findDueForBilling(status='ACTIVE' 고정)의 결과를 다시
     * status == CANCELLED 로 걸러서 항상 빈 리스트였고, Free 전환 코드가 도달 불가능한
     * 죽은 코드였다. 그래서 구독을 취소해도 planType 이 그대로 남아 유료 기능을
     * 무기한 사용할 수 있었다.
     *
     * ## 기간이 NULL 인 행도 대상이다
     *
     * `current_period_end` 는 `NOT NULL` 이 아니고 기본값도 없다
     * (`V1__init_schema.sql:327`). 그런데 SQL 에서 **NULL 과의 `<` 비교는 참이 아니라
     * UNKNOWN** 이라, 조건을 `current_period_end < now` 로만 두면 기간이 비어 있는 취소
     * 구독은 **영원히 선택되지 않는다.** 그 행은 다른 조회에도 걸리지 않는다 —
     * [findTrialExpired] 는 `trial_*` 을 요구하고, [findDueForBilling]·[findPastDue]·
     * [findPausedToResume] 는 다른 status 를 요구하며, [findWithPendingPlanType] 은
     * 취소 경로가 `pending_*` 을 비우기 때문에 비어 있다. 결과적으로 **유료 planType 이
     * 영구히 남는다.**
     *
     * ## 정책: 기간을 모르면 즉시 전환한다
     *
     * 기간이 비어 있다는 것은 **보호할 잔여 기간을 알 수 없다**는 뜻이다. 모르는 기간을
     * "아직 남아 있다"고 가정하면 유료 권한이 무기한 유지되고, 그 손해는 되돌릴 수 없다.
     * 반대로 즉시 전환은 사용자가 다시 결제하면 복구된다. 그래서 **알 수 없는 기간은
     * 만료된 것으로 본다.**
     *
     * 기간이 실제로 남아 있는 정상 취소 구독은 `current_period_end` 가 채워져 있으므로
     * 이 분기에 걸리지 않는다 — 기간이 끝난 뒤에 첫 조건으로 잡힌다.
     */
    override fun findCancelledExpired(now: LocalDateTime): List<Subscription> =
        dsl.select()
            .from(SUBSCRIPTIONS)
            .where(STATUS_TEXT.eq(SubscriptionStatus.CANCELLED.name))
            .and(CURRENT_PERIOD_END.lessThan(now).or(CURRENT_PERIOD_END.isNull))
            .fetch()
            .map { it.toSubscription() }

    /**
     * 만료 처리 대상 트라이얼.
     *
     * status 가 TRIALING 인 것만 보면, 어떤 이유로든 status 가 덮여 쓰인 트라이얼은
     * 영원히 만료되지 않고 유료 플랜이 무기한 유지된다. 그래서 "결제 수단이 없는데
     * (paddleSubscriptionId IS NULL) 트라이얼 종료일이 지난" 건까지 함께 회수한다.
     *
     * ## 이미 회수된 건은 제외한다
     *
     * 만료 처리는 `plan_type`·`status` 를 FREE 로 내리지만 **`trial_start`·`trial_end` 는
     * 지우지 않는다**(체험을 썼다는 사실은 재사용 방지에 필요하다). 그래서 두 번째 조건
     * (`paddle_subscription_id IS NULL AND trial_start IS NOT NULL`)이 회수가 끝난 행에도
     * 계속 참이었고, 그 행이 **매일 밤 다시 조회돼** "트라이얼 만료" 알림이 반복 발송됐다.
     * 크레딧은 `applyPlanEntitlement` 가 하향에서 올려주지 않아 안전했지만, 사용자에게는
     * 같은 알림이 끝없이 쌓였다.
     *
     * `plan_type <> 'FREE'` 로 거른다. 회수의 목적이 "유료 플랜이 무기한 유지되는 것"을
     * 막는 것이므로, 이미 FREE 인 행은 회수할 대상이 없다. status 가 아니라 plan_type 을
     * 보는 이유는 status 가 덮여 쓰인 경우에도 유료 플랜은 잡아내야 하기 때문이다 —
     * 원래 두 번째 조건이 존재하는 이유와 같다.
     *
     * 정상적으로 유료 전환된 구독은 `completeSubscription` 이 `trial_end` 를 null 로
     * 지우므로 첫 조건(`trial_end <= now`)에서 이미 제외된다.
     */
    override fun findTrialExpired(now: LocalDateTime): List<Subscription> =
        dsl.select()
            .from(SUBSCRIPTIONS)
            .where(TRIAL_END.lessOrEqual(now))
            .and(PLAN_TYPE_TEXT.ne(PlanType.FREE.name))
            .and(
                STATUS_TEXT.eq(SubscriptionStatus.TRIALING.name)
                    .or(PADDLE_SUBSCRIPTION_ID.isNull.and(TRIAL_START.isNotNull)),
            )
            .fetch()
            .map { it.toSubscription() }

    override fun findPausedToResume(now: LocalDateTime): List<Subscription> =
        dsl.select()
            .from(SUBSCRIPTIONS)
            .where(STATUS_TEXT.eq(SubscriptionStatus.PAUSED.name))
            .and(RESUME_AT.lessOrEqual(now))
            .fetch()
            .map { it.toSubscription() }

    private fun Record.toSubscription(): Subscription {
        val billingCycleStr = get(BILLING_CYCLE)
        val planTypeStr = get(PLAN_TYPE) ?: "FREE"
        val statusStr = get(STATUS) ?: "FREE"
        val pendingPlanTypeStr = get(PENDING_PLAN_TYPE)
        val pendingBillingCycleStr = get(PENDING_BILLING_CYCLE)

        return Subscription(
            id = get(ID),
            userId = get(USER_ID),
            planType = try { PlanType.valueOf(planTypeStr) } catch (_: Exception) { PlanType.FREE },
            status = try { SubscriptionStatus.valueOf(statusStr) } catch (_: Exception) { SubscriptionStatus.FREE },
            price = get(PRICE),
            billingCycle = billingCycleStr?.let { try { BillingCycle.valueOf(it) } catch (_: Exception) { BillingCycle.MONTHLY } } ?: BillingCycle.MONTHLY,
            currentPeriodStart = localDateTime(CURRENT_PERIOD_START),
            currentPeriodEnd = localDateTime(CURRENT_PERIOD_END),
            nextBillingDate = localDateTime(NEXT_BILLING_DATE),
            pendingPlanType = pendingPlanTypeStr?.let { try { PlanType.valueOf(it) } catch (_: Exception) { null } },
            pendingBillingCycle = pendingBillingCycleStr?.let { try { BillingCycle.valueOf(it) } catch (_: Exception) { null } },
            billingKeyEncrypted = get(BILLING_KEY_ENCRYPTED),
            storageQuotaLimitBytes = get(STORAGE_QUOTA_LIMIT_BYTES),
            paddleSubscriptionId = get(PADDLE_SUBSCRIPTION_ID),
            paddleCustomerId = get(PADDLE_CUSTOMER_ID),
            cancelledAt = localDateTime(CANCELLED_AT),
            trialStart = localDateTime(TRIAL_START),
            trialEnd = localDateTime(TRIAL_END),
            trialPlanType = get(TRIAL_PLAN_TYPE)?.let { try { PlanType.valueOf(it) } catch (_: Exception) { null } },
            pausedAt = localDateTime(PAUSED_AT),
            resumeAt = localDateTime(RESUME_AT),
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
    }
}
