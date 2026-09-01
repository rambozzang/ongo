package com.ongo.application.subscription

import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.domain.credit.CreditRepository
import com.ongo.domain.notification.Notification
import com.ongo.domain.notification.NotificationRepository
import com.ongo.common.enums.NotificationType
import com.ongo.domain.lock.DistributedLockPort
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

@Component
class BillingScheduler(
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository,
    private val notificationRepository: NotificationRepository,
    private val creditRepository: CreditRepository,
    private val distributedLockPort: DistributedLockPort,
    /** 플랜 전환 시 무료 크레딧 권한을 한 곳에서 맞춘다. */
    private val creditService: com.ongo.application.credit.CreditService,
    /**
     * 건별 갱신.
     *
     * 별도 빈인 이유는 둘이다. 같은 클래스 메서드를 부르면 프록시를 지나지 않아 전파
     * 설정이 무시되고, 갱신은 **한 호출 안에서 커밋이 두 번** 일어나야 해서 애노테이션으로
     * 표현할 수 없다.
     */
    private val renewalService: SubscriptionRenewalService,
    transactionManager: PlatformTransactionManager,
    /**
     * 정기 청구 실행 여부. **기본값은 꺼짐이다.**
     *
     * 켜는 순간 스케줄러가 고객 카드에 실제로 청구하고, 실패한 구독을 PAST_DUE 로 내려
     * 7일 뒤 Free 로 강등한다. 되돌리기 가장 비싼 동작이라 배포만으로 시작되면 안 된다.
     *
     * 특히 지금 운영 데이터에는 기간·금액이 비어 있는 구독이 있고 아무도 빌링키를
     * 등록한 적이 없다. 그대로 켜면 전원이 BILLING_KEY_MISSING 으로 PAST_DUE 가 된다.
     *
     * 켜기 전 전제는 `docs/operations/SUBSCRIPTION_RENEWAL_ROLLOUT.md` 에 있다.
     */
    @param:Value("\${subscription.renewal.enabled:false}")
    private val renewalEnabled: Boolean,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val lockId = javaClass.name.hashCode().toLong()

    /**
     * 건별 격리 경계. **`REQUIRES_NEW` 여야 한다.**
     *
     * 예외를 잡는 것만으로는 격리가 되지 않는다. 건 처리 안에서 부르는
     * `creditService.applyPlanEntitlement` 는 `@Transactional`(REQUIRED)이라 바깥
     * 트랜잭션이 있으면 거기에 참여한다. 그것이 실패하면 Spring 이 **바깥 트랜잭션을
     * rollbackOnly 로 표시**하므로, 예외를 삼켜도 마지막 커밋이
     * `UnexpectedRollbackException` 으로 터지고 그날 처리 전체가 되돌아간다. 로그만
     * "건너뛰고 계속" 이라 말하는 가짜 격리가 된다.
     *
     * 그래서 건마다 별도 트랜잭션을 연다. 한 건의 상태·크레딧·알림은 지금처럼 함께
     * 커밋되거나 함께 롤백되고, 그 실패가 다른 건이나 배치 전체를 되돌리지 않는다.
     *
     * **이것이 유일한 트랜잭션 경계다.** 예전에는 단계 전체를 감싸는 `legacyTx` 가 따로
     * 있었지만, 모든 쓰기가 건별 REQUIRES_NEW 로 들어가면서 그 바깥 경계는 매 건마다
     * 자기를 suspend/resume 시키고 루프 내내 커넥션만 붙잡는 일만 남았다. 지워서 경계를
     * 하나로 줄였다 — 조회는 auto-commit 으로 돌고, 쓰기는 건별로 원자적이다.
     */
    private val perItemTx = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    @Scheduled(cron = "0 0 2 * * *") // 매일 새벽 2시
    /**
     * **의도적으로 `@Transactional` 이 없다.**
     *
     * 예전에는 이 메서드 전체가 한 트랜잭션이었다. 갱신이 붙으면서 그 안에서 PortOne 을
     * 부르게 되는데, 그러면 PG 응답을 기다리는 내내 DB 커넥션과 트랜잭션이 잡혀 있다.
     * 갱신 대상이 수십 건이면 커넥션 풀이 외부 지연에 통째로 묶인다.
     *
     * 그래서 갱신은 트랜잭션 **밖에서** 돌린다 — 경계는 [SubscriptionRenewalService] 가
     * 직접 잡는다. 하향 적용과 나머지 기존 처리(체험 만료·유예·취소)는 단계 전체가 아니라
     * **건별로** [perItemTx] 안에서 돈다.
     */
    fun processBilling() {
        // tryLock/releaseLock 은 획득과 해제가 다른 커넥션에서 일어나 락이 누수된다.
        // PostgreSQL 자문 락은 세션 범위라 다른 커넥션에서 해제해도 풀리지 않는다.
        val ran = distributedLockPort.withLock(lockId) {
            val now = LocalDateTime.now()
            /*
             * **하향 예약 적용 → 갱신 → 나머지 기존 처리** 순서를 지킨다.
             *
             * 하향 예약을 먼저 적용해야 기간 경계에서 옛 플랜으로 잘못 청구하거나,
             * 자동 갱신의 completeSubscription 이 pendingPlanType 을 지워 하향 의사를
             * 잃어버리는 일이 없다. 하향 적용은 짧은 트랜잭션으로 먼저 커밋하고, PG 호출은
             * 여전히 트랜잭션 밖에서 실행한다.
             *
             * 갱신을 나머지 유예 처리보다 먼저 두는 이유는 유지한다. 실패한 갱신이 만든
             * PAST_DUE 를 같은 실행의 유예 블록이 볼 수 있어야 3일 알림·7일 Free 전환이
             * 하루 늦지 않는다.
             */
            /*
             * 세 단계를 서로 격리한다. 한 단계가 통째로 실패해도(주로 조회 자체가 깨질
             * 때) 나머지는 계속 간다. 건별 격리만으로는 부족하다 — 목록 조회가 터지면
             * 그 예외는 여전히 위로 올라와 뒤 단계를 막는다.
             *
             * 순서는 그대로다. 하향 예약을 먼저 적용해야 기간 경계에서 옛 플랜으로 잘못
             * 청구하지 않고, 갱신을 유예 처리보다 먼저 두어야 실패한 갱신이 만든 PAST_DUE
             * 를 같은 실행의 유예 블록이 볼 수 있다.
             */
            runPhase("하향 예약 적용") { processPendingDowngrades(now) }
            runPhase("자동 갱신") { processRenewals(now) }
            runPhase("기존 처리") { processDueSubscriptions() }
        }
        if (!ran) log.debug("다른 인스턴스에서 빌링 처리 실행 중, 스킵")
    }

    /**
     * 주기가 지난 ACTIVE 구독을 갱신한다.
     *
     * **여기서 트랜잭션을 열지 않는다.** 갱신은 선점 커밋 → 외부 청구 → 정산 커밋으로
     * 나뉘고, 그 경계는 [SubscriptionRenewalService] 가 직접 잡는다. 여기서 한 번 더
     * 감싸면 외부 청구가 그 트랜잭션 안에 들어가, 청구 성공 후 DB 갱신이 실패할 때 선점
     * 기록까지 롤백되어 다음 실행이 같은 주기를 재청구한다 — 돈이 두 번 빠져나간다.
     *
     * 건별 예외는 삼킨다. 한 건이 나머지 전부를 막으면 안 된다.
     */
    private fun processRenewals(now: LocalDateTime) {
        /*
         * 꺼져 있으면 **조회조차 하지 않는다.**
         *
         * findDueForBilling 만 돌려도 부작용은 없지만, 꺼진 기능이 매일 질의를 날리면
         * 로그와 지표에 "갱신이 돌고 있다"는 흔적이 남아 켜졌는지 여부를 헷갈리게 한다.
         */
        if (!renewalEnabled) {
            log.info("구독 자동 갱신이 꺼져 있어 건너뛴다. subscription.renewal.enabled=false")
            return
        }

        val due = subscriptionRepository.findDueForBilling(now)
        if (due.isEmpty()) return

        log.info("구독 갱신 대상 {}건", due.size)
        due.forEach { subscription ->
            runCatching { renewalService.renew(subscription, now) }
                .onFailure { log.error("구독 갱신 처리 실패. subscriptionId={}", subscription.id, it) }
        }
    }

    /** 기간 경계의 하향 예약을 갱신보다 먼저 적용한다. */
    private fun processPendingDowngrades(now: LocalDateTime) {
        val pendingDowngrades = subscriptionRepository.findWithPendingPlanType()
            .filter {
                // 해지·미납·일시정지 구독을 ACTIVE로 되살리면 결제 없이 상태가 바뀌고,
                // 이어지는 갱신 조회가 고객에게 다시 청구할 수 있다. 그런 상태의 예약은
                // 해당 상태 처리(Free 전환·재개)가 맡도록 보류한다.
                if (it.status != SubscriptionStatus.ACTIVE) return@filter false
                /*
                 * **기간을 모르면 만료된 것으로 본다.**
                 *
                 * `current_period_end`·`next_billing_date` 는 둘 다 NOT NULL 이 아니다
                 * (`V1__init_schema.sql`). 예전에는 둘 다 비어 있으면 `boundary` 가 null 이라
                 * 두 비교가 모두 false 가 되어 **그 예약이 영원히 적용되지 않았다.** 로그도
                 * 남지 않아 아무도 알 수 없었다.
                 *
                 * 그 침묵이 향하는 결과는 한쪽뿐이다 — 하향을 요청한 사용자가 상위 플랜에
                 * 무기한 남는다. `changePlan` 은 그때 `effectiveDate = currentPeriodEnd ?: now`,
                 * 즉 **"지금 적용된다"** 고 응답해 놓고 실제로는 적용하지 않는다.
                 *
                 * 정책은 이미 정해져 있다. `SubscriptionJooqRepository.findCancelledExpired`
                 * 가 같은 상황을 이렇게 다룬다 — *"기간이 비어 있다는 것은 보호할 잔여 기간을
                 * 알 수 없다는 뜻이다. 모르는 기간을 아직 남아 있다고 가정하면 유료 권한이
                 * 무기한 유지되고, 그 손해는 되돌릴 수 없다. 반대로 즉시 전환은 사용자가 다시
                 * 결제하면 복구된다."* 취소와 하향이 서로 다른 답을 낼 이유가 없다.
                 *
                 * 기간이 실제로 남아 있는 정상 구독은 `currentPeriodEnd` 가 채워져 있어 이
                 * 분기에 걸리지 않는다 — 종전처럼 기간이 끝난 뒤에만 적용된다.
                 */
                val boundary = it.currentPeriodEnd ?: it.nextBillingDate
                boundary == null || !boundary.isAfter(now)
        }
        /*
         * **건별로 격리한다.** 하향 적용은 갱신과 나머지 처리보다 **먼저** 실행되므로,
         * 여기서 한 건이 터지면 남은 하향 예약뿐 아니라 자동 갱신과 체험 만료·유예·취소
         * 처리까지 그날 전부 멈춘다. 그러면 기간 경계에서 옛 플랜으로 청구하지 않으려고
         * 이 블록을 앞에 둔 의도가 정확히 뒤집힌다 — 하향이 적용되지 않은 채 갱신만
         * 건너뛰는 것이 아니라, 다음 날 하향 전 플랜으로 청구될 수 있다.
         */
        pendingDowngrades.forEachIsolated("하향 예약 적용") { sub -> applyPendingDowngrade(sub, now) }
    }

    /**
     * 예약된 플랜·주기를 한 덩어리로 적용한다.
     *
     * `pendingPlanType` 과 `pendingBillingCycle` 은 **함께** 반영되고 함께 지워져야 한다.
     * 하나만 반영되면 가격과 주기가 어긋난 구독이 남는다 — 예컨대 주기는 연간인데 가격은
     * 월간이면 다음 청구가 1/12 만 걷힌다.
     */
    private fun applyPendingDowngrade(sub: Subscription, now: LocalDateTime) {
        val newPlan = sub.pendingPlanType
        if (newPlan == null) {
            // 주기만 남은 행은 적용할 플랜이 없다. 경고만 남기고 그대로 둔다.
            log.error(
                "예약 플랜 없이 결제 주기만 남은 구독입니다. 수동 확인이 필요합니다. subscriptionId={}",
                sub.id,
            )
            return
        }
        val newBillingCycle = sub.pendingBillingCycle ?: sub.billingCycle
        subscriptionRepository.update(sub.copy(
            planType = newPlan,
            price = newPlan.priceFor(newBillingCycle),
            billingCycle = newBillingCycle,
            pendingPlanType = null,
            pendingBillingCycle = null,
            status = if (newPlan == PlanType.FREE) SubscriptionStatus.FREE else SubscriptionStatus.ACTIVE,
            /*
             * 유료 시절의 저장공간 오버라이드를 함께 거둔다.
             *
             * 이 컬럼은 `StorageQuotaUseCase.getEffectiveLimit` 에서 플랜을 **무시하는**
             * 절대값으로 읽힌다. 비우지 않으면 플랜만 내려가고 저장공간은 유료 그대로
             * 남아, 결제 없이 유료 한도를 계속 쓰게 된다. 비우면 fallback 이 새 플랜의
             * `storageBytes` 를 돌려준다.
             */
            storageQuotaLimitBytes = null,
            updatedAt = now,
        ))
        val user = userRepository.findById(sub.userId)
        if (user != null) {
            userRepository.update(user.copy(planType = newPlan))
        }
        /*
         * 예전에는 freeMonthly 만 바꿨다. 그러면 다음 달 리셋 전까지 freeRemaining 과
         * balance 가 이전 플랜 기준으로 남아, 하향했는데도 상위 플랜만큼 쓸 수 있었다.
         * 상향(paid→paid)에서는 반대로 한도만 오르고 실제 잔여는 그대로였다.
         *
         * entitlement API 는 freeMonthly·freeRemaining·balance 를 한 번에 맞추고,
         * 대상이 FREE 면 잔여를 내리고 유료면 올린다.
         */
        creditService.applyPlanEntitlement(sub.userId, newPlan, reason = "PENDING_PLAN_CHANGE")
        notificationRepository.save(Notification(
            userId = sub.userId,
            type = NotificationType.SYSTEM,
            title = "플랜 변경 완료",
            message = "${sub.planType.displayName}에서 ${newPlan.displayName}으로 플랜이 변경되었습니다.",
        ))
        log.info("다운그레이드 적용: userId={}, {} → {}", sub.userId, sub.planType, newPlan)
    }

    /** 하향 적용을 제외한 나머지 기존 처리. 한 트랜잭션 안에서 돈다. */
    /**
     * 체험 하나를 Free 로 되돌린다. **의미는 종전과 같다** — 구독 상태·사용자 플랜·크레딧
     * 회수·알림이 한 덩어리로 커밋되거나 함께 롤백된다. 달라진 것은 그 덩어리의 범위가
     * 배치 전체가 아니라 이 한 건이라는 점뿐이다.
     */
    private fun expireTrial(sub: Subscription, now: LocalDateTime) {
        subscriptionRepository.update(sub.copy(
            planType = PlanType.FREE,
            status = SubscriptionStatus.FREE,
            price = 0,
            pendingPlanType = null,
            pendingBillingCycle = null,
            /*
             * 유료 시절의 저장공간 오버라이드를 함께 거둔다.
             *
             * 이 컬럼은 `StorageQuotaUseCase.getEffectiveLimit` 에서 플랜을 **무시하는**
             * 절대값으로 읽힌다. 비우지 않으면 플랜만 내려가고 저장공간은 유료 그대로
             * 남아, 결제 없이 유료 한도를 계속 쓰게 된다. 비우면 fallback 이 새 플랜의
             * `storageBytes` 를 돌려준다.
             */
            storageQuotaLimitBytes = null,
            updatedAt = now,
        ))
        val user = userRepository.findById(sub.userId)
        if (user != null) {
            userRepository.update(user.copy(planType = PlanType.FREE))
        }
        // 체험으로 받은 유료 크레딧이 만료 후에도 남으면 권한이 새어나간다.
        creditService.applyPlanEntitlement(sub.userId, PlanType.FREE, reason = "TRIAL_EXPIRED")
        notificationRepository.save(Notification(
            userId = sub.userId,
            type = NotificationType.SYSTEM,
            title = "트라이얼 만료",
            message = "7일 무료 체험이 종료되어 Free 플랜으로 전환되었습니다.",
        ))
        log.info("트라이얼 만료 → Free 전환: userId={}", sub.userId)
    }

    /** 단계 단위 격리. 목록 조회가 깨져도 다른 단계는 계속 간다. */
    private fun runPhase(name: String, block: () -> Unit) {
        runCatching(block).onFailure { log.error("빌링 단계 실패로 건너뛴다. phase={}", name, it) }
    }

    /**
     * 건별로 격리해 처리한다. 한 건이 실패해도 나머지 건과 뒤따르는 블록은 계속 간다.
     *
     * `runCatching` 만으로는 격리가 되지 않는다 — 이유는 [perItemTx] 선언부에 있다.
     */
    private fun List<Subscription>.forEachIsolated(action: String, block: (Subscription) -> Unit) {
        forEach { sub ->
            runCatching { perItemTx.execute { block(sub) } }
                .onFailure { e ->
                    // 삼키되 찾을 수 있게 남긴다. 운영자가 이 건만 따로 처리해야 한다.
                    log.error(
                        "{} 처리 실패로 이 건을 건너뛴다. subscriptionId={} userId={}",
                        action,
                        sub.id,
                        sub.userId,
                        e,
                    )
                }
        }
    }

    /**
     * 일시정지 만료 → 재개. 상태와 알림이 한 덩어리다.
     *
     * **청구창이 비어 있는 유료 구독은 재개하지 않는다.** 재개는 결제를 거치지 않고 상태만
     * 되돌리므로, 기간이 없는 채로 ACTIVE 가 되면 그 구독은 어떤 만료·갱신 조회에도 걸리지
     * 않아 결제 없이 유료 권한이 무기한 유지된다
     * ([Subscription.missingPaidBillingWindow] 참고).
     *
     * 예외로 끝내면 `forEachIsolated` 가 이 건만 건너뛰고 로그를 남긴다 — 다른 구독의 재개는
     * 계속되고, 이 건은 운영자가 따로 확인하게 된다. 조용히 넘기면 일시정지가 끝난 줄 알고
     * 있는 구독이 영영 PAUSED 로 남는다. FREE 구독은 종전 그대로 재개된다.
     */
    private fun resumePaused(sub: Subscription, now: LocalDateTime) {
        val missing = sub.missingPaidBillingWindow()
        check(missing.isEmpty()) {
            "청구 기간이 없는 유료 구독은 재개할 수 없습니다. " +
                "비어 있는 값: ${missing.joinToString()} " +
                "(subscriptionId=${sub.id}, plan=${sub.planType.name}). 결제 기간을 먼저 확정하세요."
        }
        subscriptionRepository.update(sub.copy(
            status = SubscriptionStatus.ACTIVE,
            pausedAt = null,
            resumeAt = null,
            updatedAt = now,
        ))
        notificationRepository.save(Notification(
            userId = sub.userId,
            type = NotificationType.SYSTEM,
            title = "구독 자동 재개",
            message = "일시정지 기간(30일)이 만료되어 구독이 자동으로 재개되었습니다.",
        ))
        log.info("일시정지 자동 재개: userId={}", sub.userId)
    }

    /** 미결제 3일 유예 알림. 알림과 상태 표시가 한 덩어리다. */
    private fun notifyPastDue(sub: Subscription) {
        notificationRepository.save(Notification(
            userId = sub.userId,
            type = NotificationType.SYSTEM,
            title = "결제 실패 알림",
            message = "결제가 실패했습니다. 3일 이내에 결제 수단을 확인해주세요."
        ))
        subscriptionRepository.update(sub.copy(status = SubscriptionStatus.PAST_DUE))
    }

    /** 미결제 7일 → Free. 구독·사용자 플랜·크레딧 회수·알림이 한 덩어리다. */
    private fun downgradePastDue(sub: Subscription, now: LocalDateTime) {
        subscriptionRepository.update(sub.copy(
            planType = PlanType.FREE,
            status = SubscriptionStatus.FREE,
            price = 0,
            pendingPlanType = null,
            pendingBillingCycle = null,
            /*
             * 유료 시절의 저장공간 오버라이드를 함께 거둔다.
             *
             * 이 컬럼은 `StorageQuotaUseCase.getEffectiveLimit` 에서 플랜을 **무시하는**
             * 절대값으로 읽힌다. 비우지 않으면 플랜만 내려가고 저장공간은 유료 그대로
             * 남아, 결제 없이 유료 한도를 계속 쓰게 된다. 비우면 fallback 이 새 플랜의
             * `storageBytes` 를 돌려준다.
             */
            storageQuotaLimitBytes = null,
            updatedAt = now
        ))
        val user = userRepository.findById(sub.userId)
        if (user != null) {
            userRepository.update(user.copy(planType = PlanType.FREE))
        }
        creditService.applyPlanEntitlement(sub.userId, PlanType.FREE, reason = "PAST_DUE")
        notificationRepository.save(Notification(
            userId = sub.userId,
            type = NotificationType.SYSTEM,
            title = "구독 만료",
            message = "결제 미처리로 Free 플랜으로 전환되었습니다."
        ))
        log.info("Free 전환: userId={}", sub.userId)
    }

    /** 취소 후 기간 종료 → Free. 구독·사용자 플랜·크레딧 회수가 한 덩어리다. */
    private fun downgradeCancelled(sub: Subscription) {
        subscriptionRepository.update(sub.copy(
            planType = PlanType.FREE,
            status = SubscriptionStatus.FREE,
            price = 0,
            pendingPlanType = null,
            pendingBillingCycle = null,
            /*
             * 유료 시절의 저장공간 오버라이드를 함께 거둔다.
             *
             * 이 컬럼은 `StorageQuotaUseCase.getEffectiveLimit` 에서 플랜을 **무시하는**
             * 절대값으로 읽힌다. 비우지 않으면 플랜만 내려가고 저장공간은 유료 그대로
             * 남아, 결제 없이 유료 한도를 계속 쓰게 된다. 비우면 fallback 이 새 플랜의
             * `storageBytes` 를 돌려준다.
             */
            storageQuotaLimitBytes = null,
        ))
        val user = userRepository.findById(sub.userId)
        if (user != null) {
            userRepository.update(user.copy(planType = PlanType.FREE))
        }
        creditService.applyPlanEntitlement(sub.userId, PlanType.FREE, reason = "SUBSCRIPTION_CANCELLED")
    }

    private fun processDueSubscriptions() {
        log.info("빌링 처리 시작")
        val now = LocalDateTime.now()

        /*
         * 트라이얼 만료 처리.
         *
         * **건별로 격리한다.** 한 건이 실패하면 그 건만 건너뛰고 나머지는 계속 처리한다.
         * 예전에는 다섯 블록이 한 트랜잭션이라, 만료 대상 한 명의 크레딧 갱신이 실패하면
         * 그날 밤 배치가 통째로 멈췄다 — 나머지 만료·일시정지 재개·유예 알림·Free 전환이
         * 전부 미뤄지고, 유료 권한이 하루 더 유지됐다.
         *
         * 격리 경계가 [perItemTx](REQUIRES_NEW)인 이유는 그 선언부에 적어 두었다.
         */
        subscriptionRepository.findTrialExpired(now)
            .forEachIsolated("트라이얼 만료") { sub -> expireTrial(sub, now) }

        // 일시정지 자동 재개 (30일 초과)
        subscriptionRepository.findPausedToResume(now)
            .forEachIsolated("일시정지 자동 재개") { sub -> resumePaused(sub, now) }

        // 미결제 3일 유예 → 알림
        subscriptionRepository.findPastDue(3)
            .forEachIsolated("미결제 3일 알림") { sub -> notifyPastDue(sub) }

        // 미결제 7일 → Free 전환
        subscriptionRepository.findPastDue(7)
            .forEachIsolated("미결제 7일 Free 전환") { sub -> downgradePastDue(sub, now) }

        // 취소된 구독 중 기간 종료된 것 → Free 전환
        // findDueForBilling 은 status='ACTIVE' 만 반환하므로 그 결과를 CANCELLED 로
        // 거르면 항상 비어 있었다(이 블록 전체가 실행되지 않았다). 전용 쿼리를 쓴다.
        subscriptionRepository.findCancelledExpired(now)
            .forEachIsolated("취소 만료 Free 전환") { sub -> downgradeCancelled(sub) }

        log.info("빌링 처리 완료")
    }
}
