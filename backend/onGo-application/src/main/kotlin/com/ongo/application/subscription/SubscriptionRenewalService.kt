package com.ongo.application.subscription

import com.ongo.application.portone.PortOneBillingChargeException
import com.ongo.application.portone.PortOneBillingChargeRequest
import com.ongo.application.portone.PortOnePayment
import com.ongo.application.portone.PortOnePaymentGateway
import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.NotificationType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.notification.Notification
import com.ongo.domain.notification.NotificationRepository
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRenewalAttempt
import com.ongo.domain.subscription.SubscriptionRenewalAttemptRepository
import com.ongo.domain.subscription.SubscriptionRenewalOutcome
import com.ongo.domain.subscription.SubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.LocalDateTime

/**
 * 주기가 지난 구독 하나를 갱신한다.
 *
 * ## 커밋 경계가 이 클래스의 전부다
 *
 * 외부 결제와 DB 쓰기를 **같은 트랜잭션에 묶지 않는다.** 묶으면 두 가지가 동시에 깨진다.
 *
 *  - 청구가 성공한 뒤 DB 갱신이 실패하면 트랜잭션이 롤백되어 선점 행까지 사라진다.
 *    다음 스케줄러가 같은 주기를 다시 청구한다 — **돈이 두 번 빠져나간다.**
 *  - 트랜잭션이 열린 채 외부 호출을 기다리므로 커넥션이 PG 응답 시간만큼 잠긴다.
 *
 * 그래서 세 단계로 나눈다.
 *
 *  1. **선점 커밋** — ATTEMPTED 행을 만들고 **즉시 커밋한다.** 이 커밋이 끝나야 청구로 간다.
 *  2. **외부 청구** — 트랜잭션 **밖에서** 부른다.
 *  3. **정산 커밋** — 결과를 채우고 구독 상태를 바꾼다.
 *
 * 3단계가 실패하거나 프로세스가 2단계에서 죽으면 ATTEMPTED 가 남는다. 그건 손실이 아니라
 * **"결과를 모른다"는 기록**이다. 다음 실행은 그 행을 보고 **재청구가 아니라 재조회**로
 * 결말을 짓는다([reconcile]).
 *
 * ## 왜 별도 빈인가
 *
 * [BillingScheduler] 가 `@Transactional` 안에서 이걸 부른다. 같은 빈의 메서드를 부르면
 * Spring 프록시를 지나지 않아 전파 설정이 무시되므로 호출 대상을 분리한다. 여기서는
 * 애노테이션 대신 [TransactionTemplate] 두 개로 경계를 **직접** 잡는다 — 한 메서드 안에서
 * 커밋이 두 번 일어나야 하는데 애노테이션으로는 표현할 수 없다.
 */
@Service
class SubscriptionRenewalService(
    private val subscriptionRepository: SubscriptionRepository,
    private val renewalAttemptRepository: SubscriptionRenewalAttemptRepository,
    private val notificationRepository: NotificationRepository,
    private val gateway: PortOnePaymentGateway,
    private val tokenEncryptionPort: TokenEncryptionPort,
    transactionManager: PlatformTransactionManager,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 선점과 정산 각각의 커밋 경계.
     *
     * 둘 다 `REQUIRES_NEW` 다. 스케줄러의 바깥 트랜잭션에 참여하면 그 트랜잭션이 끝날 때까지
     * 커밋되지 않아, 선점을 먼저 확정한다는 이 설계의 전제가 무너진다.
     */
    private val claimTx = requiresNew(transactionManager)
    private val settleTx = requiresNew(transactionManager)

    private fun requiresNew(manager: PlatformTransactionManager) =
        TransactionTemplate(manager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

    /** 이번 호출이 확정한 결과. 이미 확정된 주기이거나 결과를 알 수 없으면 null. */
    fun renew(subscription: Subscription, now: LocalDateTime): SubscriptionRenewalOutcome? {
        val subscriptionId = subscription.id ?: return null
        val periodStart = subscription.currentPeriodEnd ?: subscription.nextBillingDate ?: return null

        // ---- 1단계: 선점 커밋 ----
        val claim = claimTx.execute { claimPeriod(subscriptionId, periodStart, now) } ?: return null

        // ---- 2단계: 외부 호출 (트랜잭션 밖) ----
        val outcome = when (claim) {
            is Claim.Fresh -> charge(subscription)
            /*
             * 앞선 실행이 결과를 못 채우고 끝난 주기다. **다시 청구하지 않는다.**
             * 그 사이 돈이 이미 빠져나갔을 수 있고, 재청구는 그것을 두 번으로 만든다.
             */
            is Claim.Stale -> reconcile(subscription)
        } ?: run {
            // 결과 불명. ATTEMPTED 를 그대로 남겨 다음 실행이 다시 재조회하게 둔다.
            log.warn(
                "구독 갱신 결과를 확정하지 못했다. 다음 실행에서 재조회한다. subscriptionId={} periodStart={}",
                subscriptionId, periodStart,
            )
            return null
        }

        // ---- 3단계: 정산 커밋 ----
        settleTx.execute { settle(subscription, claim.attemptId, outcome, now) }
        return outcome
    }

    // ---- 1단계 ----

    private sealed interface Claim {
        val attemptId: Long

        /** 이번 실행이 새로 잡은 주기. 청구로 간다. */
        data class Fresh(override val attemptId: Long) : Claim

        /** 앞선 실행이 결과를 못 채운 주기. 재조회로 간다. */
        data class Stale(override val attemptId: Long) : Claim
    }

    private fun claimPeriod(subscriptionId: Long, periodStart: LocalDateTime, now: LocalDateTime): Claim? {
        val fresh = renewalAttemptRepository.claimPeriod(
            SubscriptionRenewalAttempt(
                subscriptionId = subscriptionId,
                periodStart = periodStart,
                outcome = SubscriptionRenewalOutcome.ATTEMPTED,
                createdAt = now,
            ),
        )
        if (fresh != null) return Claim.Fresh(fresh)

        /*
         * 선점하지 못한 이유를 가른다. UNIQUE 가 동시 선점을 막아 준 것이므로 여기 온 것
         * 자체는 정상이다. 문제는 그 행이 이미 끝난 주기인지, 결과를 못 채운 주기인지다.
         */
        val existing = renewalAttemptRepository.findByPeriod(subscriptionId, periodStart)
        if (existing == null) {
            log.warn("선점에 실패했는데 기존 행도 없다. subscriptionId={} periodStart={}", subscriptionId, periodStart)
            return null
        }
        if (existing.outcome != SubscriptionRenewalOutcome.ATTEMPTED) {
            log.debug("이미 확정된 갱신 주기라 건너뛴다. subscriptionId={} outcome={}", subscriptionId, existing.outcome)
            return null
        }
        /*
         * 방금 선점된 행은 **아직 진행 중일 수 있다.** 선점은 커밋됐지만 청구는 그 뒤
         * 트랜잭션 밖에서 일어나므로, 이 창에서 재조회하면 아직 만들어지지 않은 결제를
         * 404 로 보고 미결제로 확정해 버린다. 유예가 지나기 전에는 손대지 않고 그대로 둔다.
         */
        if (existing.createdAt.isAfter(now.minus(STALE_ATTEMPT_GRACE))) {
            log.debug(
                "선점 직후라 아직 진행 중일 수 있다. 이번 실행에서는 건드리지 않는다. subscriptionId={} claimedAt={}",
                subscriptionId, existing.createdAt,
            )
            return null
        }
        return Claim.Stale(existing.id)
    }

    // ---- 2단계 ----

    /**
     * 실제 청구. 빌링키가 없으면 **시도하지 않는다.**
     *
     * 현재 결제 UI 가 빌링키를 발급하지 않으므로 기존 구독은 전부 이 경로다.
     */
    private fun charge(subscription: Subscription): SubscriptionRenewalOutcome? {
        val billingKey = decryptBillingKey(subscription) ?: return SubscriptionRenewalOutcome.BILLING_KEY_MISSING

        return try {
            val payment = gateway.payWithBillingKey(
                PortOneBillingChargeRequest(
                    paymentId = renewalPaymentId(subscription),
                    billingKey = billingKey,
                    orderName = "${subscription.planType.displayName} 구독 갱신",
                    customerId = subscription.userId.toString(),
                    amount = subscription.price,
                ),
            )
            judge(subscription, payment)
        } catch (e: PortOneBillingChargeException) {
            /*
             * 청구 호출이 실패했다. 하지만 **PG 에 닿지 못한 것인지, 승인 뒤 응답만 못 받은
             * 것인지 구분할 수 없다.** 실패로 단정하면 이미 결제된 고객을 PAST_DUE 로
             * 내리게 되므로, 같은 paymentId 로 다시 조회해 사실을 확인한다.
             */
            log.warn("구독 갱신 청구 호출 실패. 재조회로 확인한다. subscriptionId={}", subscription.id, e)
            reconcile(subscription)
        }
    }

    /**
     * 청구하지 않고 **조회만으로** 결말을 짓는다.
     *
     * 재청구는 절대 하지 않는다. 이 경로에 온 주기는 이미 돈이 움직였을 수 있다.
     *
     * @return 확정된 결과. 조회 자체가 실패해 아직 알 수 없으면 null.
     */
    private fun reconcile(subscription: Subscription): SubscriptionRenewalOutcome? {
        val paymentId = renewalPaymentId(subscription)
        val payment = try {
            gateway.findPayment(paymentId)
        } catch (e: Exception) {
            // 결제됐는지 아닌지 모른다. 모르는 것을 실패로 적으면 안 된다.
            log.error("구독 갱신 재조회 실패. 결과 불명으로 남긴다. subscriptionId={}", subscription.id, e)
            return null
        }

        if (payment == null) {
            /*
             * PG 가 이 id 를 모른다 = 청구가 닿지 못했다 = **돈이 움직이지 않았다.**
             * 여기서만 실패로 단정할 수 있다. 그래도 자동 재청구는 하지 않는다 —
             * 구독을 PAST_DUE 로 내려 고객이 직접 다시 결제하게 한다.
             */
            log.info("구독 갱신 결제가 PG 에 없다. 미결제로 확정한다. subscriptionId={}", subscription.id)
            return SubscriptionRenewalOutcome.CHARGE_FAILED
        }
        return judge(subscription, payment)
    }

    /**
     * PG 가 알려준 결제 하나를 우리 기준으로 판정한다.
     *
     * 금액을 다시 본다. 웹훅 경로와 같은 원칙이다 — 우리가 보낸 숫자를 우리가 검증하는
     * 것이 아니라 PG 가 실제로 승인한 금액을 본다.
     */
    private fun judge(subscription: Subscription, payment: PortOnePayment): SubscriptionRenewalOutcome {
        if (!PAID_STATUSES.contains(payment.status.uppercase())) {
            log.info("구독 갱신 결제가 완료 상태가 아니다. subscriptionId={} status={}", subscription.id, payment.status)
            return SubscriptionRenewalOutcome.CHARGE_FAILED
        }
        if (payment.amount != subscription.price) {
            // 돈은 움직였는데 금액이 다르다. 자동으로 정할 수 없다.
            log.error(
                "구독 갱신 승인 금액 불일치. 사람이 확인해야 한다. subscriptionId={} expected={} actual={}",
                subscription.id, subscription.price, payment.amount,
            )
            return SubscriptionRenewalOutcome.NEEDS_REVIEW
        }
        /*
         * 통화도 본다. 금액만 맞추면 19,900 KRW 청구가 19,900 USD 승인으로 돌아와도
         * 성공으로 잡힌다 — 숫자는 같고 실제로 빠져나간 돈은 천 배 넘게 다르다.
         * `PortOnePaymentService` 의 완료 검증도 금액과 통화를 함께 본다(:317-322).
         *
         * 여기서는 예외 대신 NEEDS_REVIEW 다. 돈이 이미 움직였으므로 구독을 내리면
         * 결제한 고객의 권한을 뺏는 것이고, 어느 쪽도 자동으로 정할 수 없다.
         */
        if (!payment.currency.equals(RENEWAL_CURRENCY, ignoreCase = true)) {
            log.error(
                "구독 갱신 승인 통화 불일치. 사람이 확인해야 한다. subscriptionId={} expected={} actual={}",
                subscription.id, RENEWAL_CURRENCY, payment.currency,
            )
            return SubscriptionRenewalOutcome.NEEDS_REVIEW
        }
        return SubscriptionRenewalOutcome.CHARGED
    }

    private fun decryptBillingKey(subscription: Subscription): String? {
        val encrypted = subscription.billingKeyEncrypted?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { tokenEncryptionPort.decrypt(EncryptedToken(encrypted)).value }
            .getOrElse {
                // 키 교체·데이터 손상. 사유에 값을 남기지 않는다.
                log.error("구독 빌링키 복호화 실패. subscriptionId={}", subscription.id)
                null
            }
    }

    /**
     * 주기당 고정 식별자.
     *
     * 이 값이 고정이라서 재조회가 성립한다. 실행마다 다른 id 를 쓰면 앞선 실행이 만든
     * 결제를 영영 찾을 수 없고, 그 순간 이중 청구를 막을 방법이 사라진다.
     */
    private fun renewalPaymentId(subscription: Subscription): String {
        val periodStart = subscription.currentPeriodEnd ?: subscription.nextBillingDate
        return "sub-${subscription.id}-renew-${periodStart?.toLocalDate()}"
    }

    // ---- 3단계 ----

    private fun settle(
        subscription: Subscription,
        attemptId: Long,
        outcome: SubscriptionRenewalOutcome,
        now: LocalDateTime,
    ) {
        renewalAttemptRepository.completeOutcome(attemptId, outcome)
        when (outcome) {
            SubscriptionRenewalOutcome.CHARGED -> extendPeriod(subscription, now)
            SubscriptionRenewalOutcome.CHARGE_FAILED,
            SubscriptionRenewalOutcome.BILLING_KEY_MISSING,
            -> markPastDue(subscription, now, outcome)
            /*
             * 돈이 이미 움직였다. 구독을 내리면 결제한 고객의 권한을 뺏는 것이므로
             * 상태를 바꾸지 않고 운영자 확인 대상으로만 남긴다.
             */
            SubscriptionRenewalOutcome.NEEDS_REVIEW ->
                log.error("구독 갱신 확인 필요. 상태를 바꾸지 않는다. subscriptionId={}", subscription.id)
            SubscriptionRenewalOutcome.ATTEMPTED ->
                error("확정되지 않은 결과로 정산할 수 없습니다: subscriptionId=${subscription.id}")
        }
    }

    private fun extendPeriod(subscription: Subscription, now: LocalDateTime) {
        val start = subscription.currentPeriodEnd ?: now
        val end = if (subscription.billingCycle == BillingCycle.YEARLY) start.plusYears(1) else start.plusMonths(1)
        subscriptionRepository.update(
            subscription.copy(
                status = SubscriptionStatus.ACTIVE,
                currentPeriodStart = start,
                currentPeriodEnd = end,
                nextBillingDate = end,
                updatedAt = now,
            ),
        )
        log.info("구독 갱신 성공. subscriptionId={} nextBillingDate={}", subscription.id, end)
    }

    /**
     * 청구하지 못한 구독을 PAST_DUE 로 내린다.
     *
     * 이 전이가 없으면 기존 유예 로직(3일 알림 → 7일 Free 전환)이 영원히 실행되지 않는다.
     * `findPastDue` 가 `status = 'PAST_DUE'` 인 행만 조회하는데, 지금까지 ACTIVE 를
     * PAST_DUE 로 바꾸는 코드가 어디에도 없었다.
     */
    private fun markPastDue(
        subscription: Subscription,
        now: LocalDateTime,
        outcome: SubscriptionRenewalOutcome,
    ) {
        subscriptionRepository.update(
            subscription.copy(status = SubscriptionStatus.PAST_DUE, updatedAt = now),
        )
        notificationRepository.save(
            Notification(
                userId = subscription.userId,
                type = NotificationType.SYSTEM,
                title = "구독 갱신에 실패했습니다",
                // 사용자가 할 수 있는 일만 적는다. 어느 설정이 빠졌는지는 알려 주지 않는다.
                message = "구독 결제가 처리되지 않았습니다. 7일 이내에 구독 화면에서 다시 결제해 주세요. " +
                    "그때까지 결제되지 않으면 Free 플랜으로 전환됩니다.",
            ),
        )
        log.info("구독 갱신 실패로 PAST_DUE 전환. subscriptionId={} outcome={}", subscription.id, outcome)
    }

    private companion object {
        /** PortOne 이 결제 완료로 보는 상태. 그 밖은 전부 미완료로 다룬다. */
        val PAID_STATUSES = setOf("PAID")

        /** 청구 통화. [PortOneBillingChargeRequest] 기본값과 같아야 한다. */
        const val RENEWAL_CURRENCY = "KRW"

        /**
         * 선점 직후의 ATTEMPTED 를 **건드리지 않는 시간.**
         *
         * 선점은 커밋되지만 청구는 그 뒤 트랜잭션 밖에서 일어난다. 그 사이에 다른 호출이
         * 같은 행을 보면 "결과를 못 채우고 죽은 주기"로 오해할 수 있는데, 실제로는 **첫
         * 호출이 아직 PG 응답을 기다리는 중**일 수 있다. 그때 재조회하면 결제가 아직
         * 생성되지 않아 404 가 나오고, 그 404 를 근거로 CHARGE_FAILED 로 확정해 버린다 —
         * 곧이어 승인될 결제를 미결제로 적고 고객을 PAST_DUE 로 내리는 것이다.
         *
         * 동시 실행 자체는 분산 락과 UNIQUE 가 막지만, 락이 만료되거나 인스턴스가 재시작한
         * 직후처럼 두 실행이 겹치는 창은 남는다. 그 창에서 조용히 틀린 결론을 내리지 않도록
         * 유예를 둔다.
         *
         * 10분: PG 호출 타임아웃(읽기 30초)보다 충분히 길고, 실제로 죽은 주기를 하루 넘게
         * 방치하지 않을 만큼 짧다.
         */
        val STALE_ATTEMPT_GRACE: Duration = Duration.ofMinutes(10)
    }
}
