package com.ongo.application.subscription

import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.domain.credit.CreditRepository
import com.ongo.domain.notification.Notification
import com.ongo.domain.notification.NotificationRepository
import com.ongo.common.enums.NotificationType
import com.ongo.domain.lock.DistributedLockPort
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
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
     * 갱신을 제외한 기존 처리(체험 만료·유예·취소·다운그레이드)를 감싸는 경계.
     *
     * 예전 `@Transactional processBilling` 이 하던 역할을 그대로 옮긴 것이다. 애노테이션을
     * 그대로 두면 PG 호출까지 그 트랜잭션에 들어가므로 범위를 좁혀 여기로 내렸다.
     */
    private val legacyTx = TransactionTemplate(transactionManager)

    @Scheduled(cron = "0 0 2 * * *") // 매일 새벽 2시
    /**
     * **의도적으로 `@Transactional` 이 없다.**
     *
     * 예전에는 이 메서드 전체가 한 트랜잭션이었다. 갱신이 붙으면서 그 안에서 PortOne 을
     * 부르게 되는데, 그러면 PG 응답을 기다리는 내내 DB 커넥션과 트랜잭션이 잡혀 있다.
     * 갱신 대상이 수십 건이면 커넥션 풀이 외부 지연에 통째로 묶인다.
     *
     * 그래서 갱신은 트랜잭션 **밖에서** 돌리고(경계는 [SubscriptionRenewalService] 가 직접
     * 잡는다), 기존 처리(체험 만료·유예·취소·다운그레이드)는 예전처럼 한 트랜잭션으로
     * 묶어 [legacyTx] 로 감싼다.
     */
    fun processBilling() {
        // tryLock/releaseLock 은 획득과 해제가 다른 커넥션에서 일어나 락이 누수된다.
        // PostgreSQL 자문 락은 세션 범위라 다른 커넥션에서 해제해도 풀리지 않는다.
        val ran = distributedLockPort.withLock(lockId) {
            val now = LocalDateTime.now()
            /*
             * 갱신을 **가장 먼저, 트랜잭션 밖에서** 돌린다.
             *
             * 먼저인 이유: 실패한 갱신이 만든 PAST_DUE 를 같은 실행의 유예 블록이 봐야
             * 3일 알림·7일 Free 전환이 하루 늦지 않는다.
             * 트랜잭션 밖인 이유: 여기서 PortOne 을 부르므로, 감싸면 외부 지연만큼
             * 커넥션이 잠기고 청구 성공 후 롤백이 선점 기록을 지울 수 있다.
             */
            processRenewals(now)
            legacyTx.execute { processDueSubscriptions() }
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

    /** 갱신을 제외한 기존 처리. 예전처럼 한 트랜잭션 안에서 돈다. */
    private fun processDueSubscriptions() {
        log.info("빌링 처리 시작")
        val now = LocalDateTime.now()

        // 트라이얼 만료 처리
        val expiredTrials = subscriptionRepository.findTrialExpired(now)
        expiredTrials.forEach { sub ->
            subscriptionRepository.update(sub.copy(
                planType = PlanType.FREE,
                status = SubscriptionStatus.FREE,
                price = 0,
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

        // 일시정지 자동 재개 (30일 초과)
        val pausedToResume = subscriptionRepository.findPausedToResume(now)
        pausedToResume.forEach { sub ->
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

        // 미결제 3일 유예 → 알림
        val pastDue3 = subscriptionRepository.findPastDue(3)
        pastDue3.forEach { sub ->
            notificationRepository.save(Notification(
                userId = sub.userId,
                type = NotificationType.SYSTEM,
                title = "결제 실패 알림",
                message = "결제가 실패했습니다. 3일 이내에 결제 수단을 확인해주세요."
            ))
            subscriptionRepository.update(sub.copy(status = SubscriptionStatus.PAST_DUE))
        }

        // 미결제 7일 → Free 전환
        val pastDue7 = subscriptionRepository.findPastDue(7)
        pastDue7.forEach { sub ->
            subscriptionRepository.update(sub.copy(
                planType = PlanType.FREE,
                status = SubscriptionStatus.FREE,
                price = 0,
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
            log.info("Free 전환: userId=${sub.userId}")
        }

        // 취소된 구독 중 기간 종료된 것 → Free 전환
        // findDueForBilling 은 status='ACTIVE' 만 반환하므로 그 결과를 CANCELLED 로
        // 거르면 항상 비어 있었다(이 블록 전체가 실행되지 않았다). 전용 쿼리를 쓴다.
        val cancelledExpired = subscriptionRepository.findCancelledExpired(now)
        cancelledExpired.forEach { sub ->
            subscriptionRepository.update(sub.copy(
                planType = PlanType.FREE,
                status = SubscriptionStatus.FREE,
                price = 0
            ))
            val user = userRepository.findById(sub.userId)
            if (user != null) {
                userRepository.update(user.copy(planType = PlanType.FREE))
            }
            creditService.applyPlanEntitlement(sub.userId, PlanType.FREE, reason = "SUBSCRIPTION_CANCELLED")
        }

        // 다운그레이드 예약 적용: pendingPlanType 설정 + 기간 만료된 구독
        val pendingDowngrades = subscriptionRepository.findWithPendingPlanType()
            .filter { it.currentPeriodEnd?.isBefore(now) == true }
        pendingDowngrades.forEach { sub ->
            val newPlan = sub.pendingPlanType ?: return@forEach
            subscriptionRepository.update(sub.copy(
                planType = newPlan,
                price = if (sub.billingCycle == BillingCycle.YEARLY) newPlan.yearlyPrice else newPlan.price,
                pendingPlanType = null,
                status = if (newPlan == PlanType.FREE) SubscriptionStatus.FREE else SubscriptionStatus.ACTIVE,
                updatedAt = now
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
                message = "${sub.planType.displayName}에서 ${newPlan.displayName}으로 플랜이 변경되었습니다."
            ))
            log.info("다운그레이드 적용: userId=${sub.userId}, ${sub.planType} → $newPlan")
        }

        log.info("빌링 처리 완료")
    }
}
