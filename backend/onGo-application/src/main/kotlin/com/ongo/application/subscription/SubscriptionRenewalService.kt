package com.ongo.application.subscription

import com.ongo.application.portone.PortOneBillingChargeException
import com.ongo.application.portone.PortOneBillingChargeRequest
import com.ongo.application.portone.PortOnePayment
import com.ongo.application.portone.PortOnePaymentGateway
import com.ongo.application.credit.CreditService
import com.ongo.application.portone.PortOnePaymentService
import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import com.ongo.domain.payment.Payment
import com.ongo.domain.payment.PaymentRepository
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
import org.springframework.beans.factory.annotation.Value
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
    /** 갱신 결제도 일반 결제와 같은 원장에 남는다. 결제 내역·대사·환불이 여기에 걸린다. */
    private val paymentRepository: PaymentRepository,
    /**
     * 정산의 단일 권위.
     *
     * 구독 기간과 크레딧 권한은 `complete` 안의 completeSubscription 한 곳에서만 적용한다.
     * 이 서비스가 직접 적용하면 웹훅 경로와 규칙이 갈라져 도착 순서에 따라 결과가 달라진다.
     */
    private val paymentService: PortOnePaymentService,
    /**
     * 청구 전에 크레딧 원장이 있는지 확인하는 데만 쓴다.
     * [CreditService.ensureAccountPresence] 는 읽기 전용이며 아무것도 만들지 않는다.
     */
    private val creditService: CreditService,
    /** PortOne 청구 본문에 상점·채널을 명시해 기본 토큰 설정에 의존하지 않는다. */
    @Value("\${payment.portone.store-id:}") private val portoneStoreId: String,
    @Value("\${payment.portone.channel-key:}") private val portoneChannelKey: String,
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
        /*
         * **레거시 Paddle 구독은 PortOne 으로 청구하지 않는다.**
         *
         * 이 서비스는 PortOne 빌링키로 청구한다. Paddle 로 결제한 구독이 여기까지 오면
         * 어느 쪽으로 가든 고객이 손해를 본다.
         *
         *  - 빌링키가 없으면 청구 실패 → PAST_DUE → 7일 뒤 Free 강등.
         *    **Paddle 에서는 정상 결제 중인데 우리 쪽에서만 권한을 뺏는다.**
         *  - 빌링키가 있으면 Paddle 과 PortOne 이 같은 주기를 각각 청구한다 — **이중 청구**다.
         *
         * `SubscriptionJooqRepository.findDueForBilling` 이 이미 같은 조건으로 걸러 내므로
         * 정상 경로에서는 여기 도달하지 않는다. 그래도 두는 이유는 그 쿼리를 지나지 않는
         * 호출자(운영 도구·재처리 배치·앞으로 생길 코드)가 **돈이 움직이는 경로를 우회**할 수
         * 있기 때문이다. 방어는 청구 직전에도 있어야 한다.
         *
         * **여기서 Paddle 갱신을 대신 처리하지 않는다.** 그것은 Paddle 웹훅의 몫이다.
         * 이 가드는 "PortOne 이 건드리지 않는다"만 말하며, 상태를 바꾸지 않고 그대로 둔다.
         */
        if (!subscription.paddleSubscriptionId.isNullOrBlank()) {
            log.info(
                "Paddle 구독은 PortOne 자동 갱신 대상이 아니라 건너뛴다. subscriptionId={} userId={}",
                subscription.id, subscription.userId,
            )
            return null
        }

        val subscriptionId = subscription.id ?: return null
        val periodStart = subscription.currentPeriodEnd ?: subscription.nextBillingDate ?: return null

        /*
         * **청구 전에** 크레딧 원장이 있는지 본다.
         *
         * 정산은 `paymentService.complete` 안의 completeSubscription 이 맡고, 그 마지막이
         * `applyPlanEntitlement` 다. 원장이 없으면 거기서 예외가 나 정산 트랜잭션이 통째로
         * 롤백된다 — 그런데 그 시점에는 **PortOne 청구가 이미 끝나 있다.** 결과는 고객
         * 카드에서 돈은 빠졌는데 기간도 크레딧도 반영되지 않은 상태이고, 다음 실행은
         * 같은 주기를 Stale 로 보고 재조회만 하므로 스스로 낫지 않는다.
         *
         * 여기서 끝내면 주기 선점(claimPeriod)도, 결제 원장 생성도, payWithBillingKey 도
         * 일어나지 않는다. 주기가 선점되지 않은 채 남으므로 원장을 복구한 뒤 다음 실행이
         * 정상적으로 청구할 수 있다 — 선점 후 실패와 달리 되돌릴 것이 없다.
         *
         * 읽기만 하며 없는 원장을 만들어 주지 않는다. 자동 생성은 그 계정의 과거 상태를
         * 추측하는 일이고, 그렇게 만든 숫자는 근거가 없다.
         *
         * 예외는 [BillingScheduler] 가 건별 `runCatching` 으로 잡아 이 구독만 건너뛴다.
         * 다른 계정의 갱신은 계속된다.
         */
        creditService.ensureAccountPresence(subscription.userId)

        // ---- 1단계: 선점 + 결제 원장 생성 (한 커밋) ----
        val claim = claimTx.execute { claimPeriod(subscription, subscriptionId, periodStart, now) }
            ?: return null

        // ---- 2단계: 외부 호출 (트랜잭션 밖) ----
        val outcome = when (claim) {
            is Claim.Fresh -> charge(subscription, claim.paymentId)
            /*
             * 앞선 실행이 결과를 못 채우고 끝난 주기다. **다시 청구하지 않는다.**
             * 그 사이 돈이 이미 빠져나갔을 수 있고, 재청구는 그것을 두 번으로 만든다.
             */
            is Claim.Stale -> reconcile(subscription, claim.paymentId)
        } ?: run {
            // 결과 불명. ATTEMPTED 를 그대로 남겨 다음 실행이 다시 재조회하게 둔다.
            log.warn(
                "구독 갱신 결과를 확정하지 못했다. 다음 실행에서 재조회한다. subscriptionId={} periodStart={}",
                subscriptionId, periodStart,
            )
            return null
        }

        // ---- 3단계: 정산 커밋 ----
        /*
         * 확정하지 못하고 끝날 수 있다. 그 사이 웹훅이 결제를 완료했다면 미결제로 적어서는
         * 안 되고, 그때는 attempt 를 ATTEMPTED 로 남겨 다음 실행이 재조회로 사실을 확인한다.
         * 그 경우는 `결과 불명` 과 같은 뜻이므로 위와 같이 null 을 돌려준다.
         */
        val confirmed = settleTx.execute { settle(subscription, claim, outcome, now) } ?: false
        return outcome.takeIf { confirmed }
    }

    // ---- 운영자 재확인 ----

    /**
     * NEEDS_REVIEW 주기를 **PG 에 다시 물어** 확정한다.
     *
     * ## 여기서 하지 않는 것
     *
     * **청구하지 않는다.** [gateway] 에서 부르는 것은 `findPayment` 하나뿐이다. 확인 대상은
     * 이미 돈이 움직였을 수 있는 주기라 재청구는 그것을 두 번으로 만든다.
     *
     * **운영자의 판단을 결과로 받지 않는다.** 인자는 attemptId 뿐이고 결과는 PG 가 정한다.
     * outcome 을 넘길 수 있게 하는 순간 "확인했다고 치고 성공 처리" 가 가능해진다.
     *
     * ## 전이 두 개만 허용한다
     *
     * - PG 가 `PAID` 이고 내부 원장의 **금액·통화와 정확히 같을 때만** CHARGED.
     *   정산은 기존 [applySettlement] → `complete` 경로를 그대로 쓴다.
     * - PG 에 결제가 없거나(`null`) `FAILED`/`CANCELLED` 일 때만 CHARGE_FAILED.
     *   결제 원장 마감과 PAST_DUE 전이도 기존 [failPayment]·[markPastDue] 를 그대로 쓴다.
     *
     * 그 밖은 **전부 상태를 바꾸지 않는다.** `READY` 같은 중간 상태, 금액·통화 불일치,
     * 내부 원장이 없는 레거시 주기가 여기 해당한다. 금액이 다른데 성공으로 적으면 틀린
     * 금액을 매출로 잡고, 실패로 적으면 실제로 빠져나간 돈을 없던 일로 만든다. 어느 쪽도
     * 코드가 정할 수 없다.
     *
     * ## 조회 예외
     *
     * `findPayment` 가 던지면 그대로 올린다. PG 장애를 "결제 없음" 으로 읽으면 승인된
     * 결제를 미결제로 확정한다. 아무것도 바꾸지 않은 채 실패하는 편이 정직하다.
     *
     * ## 동시 요청
     *
     * 승자는 [SubscriptionRenewalAttemptRepository.resolveReviewOutcome] 의 조건부 갱신이
     * 정한다. 진 쪽은 0행을 받아 정산에 손대지 않고 [RenewalReviewDecision.ALREADY_RESOLVED]
     * 로 끝난다.
     */
    fun recheckReview(attemptId: Long, now: LocalDateTime): RenewalReviewRecheck {
        val attempt = renewalAttemptRepository.findById(attemptId)
            ?: return RenewalReviewRecheck(RenewalReviewDecision.NOT_FOUND, "해당 갱신 기록을 찾을 수 없습니다.")
        if (attempt.outcome != SubscriptionRenewalOutcome.NEEDS_REVIEW) {
            return RenewalReviewRecheck(
                RenewalReviewDecision.NOT_UNDER_REVIEW,
                "확인 대상이 아닙니다. 현재 결과: ${attempt.outcome.name}",
                attempt.outcome,
            )
        }
        /*
         * V103 이전 주기다. 가리킬 내부 결제 원장이 없어 `complete` 에 넣을 수도, 닫을
         * 대상도 없다. 여기서 행을 만들어 넣으면 원장을 사후에 지어내는 것이다.
         */
        val paymentId = attempt.paymentId
            ?: return RenewalReviewRecheck(
                RenewalReviewDecision.MANUAL_ONLY,
                "내부 결제 원장이 없는 과거 주기입니다. 자동으로 확정할 수 없어 수기 대사가 필요합니다.",
            )
        val payment = paymentRepository.findById(paymentId)
            ?: return RenewalReviewRecheck(
                RenewalReviewDecision.MANUAL_ONLY,
                "연결된 결제 원장을 찾을 수 없습니다. 자동으로 확정할 수 없습니다.",
            )
        val subscription = subscriptionRepository.findById(attempt.subscriptionId)
            ?: return RenewalReviewRecheck(
                RenewalReviewDecision.MANUAL_ONLY,
                "연결된 구독을 찾을 수 없습니다. 자동으로 확정할 수 없습니다.",
            )

        // 외부 조회는 트랜잭션 밖이다. 여기서 예외가 나면 아무것도 바뀌지 않은 상태로 끝난다.
        val verified = gateway.findPayment(externalPaymentId(paymentId))
        val status = verified?.status?.uppercase()

        val target = when {
            status != null && PAID_STATUSES.contains(status) -> {
                if (verified.amount != payment.amount) {
                    return RenewalReviewRecheck(
                        RenewalReviewDecision.STILL_UNDER_REVIEW,
                        "PG 승인 금액이 내부 결제 금액과 다릅니다. 자동으로 확정하지 않았습니다.",
                    )
                }
                if (!verified.currency.equals(payment.currency, ignoreCase = true)) {
                    return RenewalReviewRecheck(
                        RenewalReviewDecision.STILL_UNDER_REVIEW,
                        "PG 승인 통화가 내부 결제 통화와 다릅니다. 자동으로 확정하지 않았습니다.",
                    )
                }
                SubscriptionRenewalOutcome.CHARGED
            }
            verified == null || status in DEFINITELY_UNPAID_STATUSES -> SubscriptionRenewalOutcome.CHARGE_FAILED
            else -> return RenewalReviewRecheck(
                RenewalReviewDecision.STILL_UNDER_REVIEW,
                "PG 결제가 아직 확정 상태가 아닙니다. 자동으로 확정하지 않았습니다.",
            )
        }

        val applied = settleTx.execute { applyRecheck(attemptId, target, paymentId, subscription, now) }
            ?: RecheckApply.LOST_RACE

        return when (applied) {
            RecheckApply.APPLIED -> {
                log.info("갱신 확인 대상을 재조회로 확정했다. attemptId={} outcome={}", attemptId, target)
                RenewalReviewRecheck(RenewalReviewDecision.RESOLVED, "PG 재조회 결과로 확정했습니다.", target)
            }
            RecheckApply.LOST_RACE -> RenewalReviewRecheck(
                RenewalReviewDecision.ALREADY_RESOLVED,
                "다른 요청이 먼저 처리했습니다. 목록을 다시 확인해 주세요.",
            )
            RecheckApply.PAYMENT_ALREADY_SETTLED -> RenewalReviewRecheck(
                RenewalReviewDecision.STILL_UNDER_REVIEW,
                "결제가 이미 완료 또는 환불로 확정돼 있어 미결제로 닫지 않았습니다. 상태를 그대로 두었습니다.",
            )
            RecheckApply.PAYMENT_MISSING -> RenewalReviewRecheck(
                RenewalReviewDecision.MANUAL_ONLY,
                "연결된 결제 원장을 찾을 수 없습니다. 자동으로 확정할 수 없습니다.",
            )
        }
    }

    /**
     * 확정과 정산을 한 트랜잭션에서 수행한다. **`settleTx` 안에서만 부른다.**
     *
     * ## 미결제 확정 전에 결제 원장을 먼저 잠그는 이유
     *
     * PG 재조회와 이 블록 사이에 웹훅이 같은 결제를 COMPLETED 로 만들 수 있다. 그때
     * attempt 만 CHARGE_FAILED 로 바꾸면 **결제는 성공인데 갱신 원장은 미결제**가 되어
     * 두 원장이 갈린다. 그 뒤로는 어느 쪽이 사실인지 코드로 판별할 수 없다.
     *
     * 그래서 잠근 결과가 이미 결말이면 [SubscriptionRenewalAttemptRepository.resolveReviewOutcome]
     * 을 **아예 부르지 않는다.** 확인 대상으로 남겨 두는 편이 정직하다 — 그 주기는 여전히
     * 사람이 봐야 하고, CHARGED 로 올리는 것도 이 경로의 권한이 아니다(그건 PG 가 PAID 이고
     * 금액·통화가 정확히 일치할 때만 한다).
     *
     * 잠금은 여기 한 번뿐이다. [failPayment] 를 쓰지 않는 것은 그 쪽이 같은 행을 다시
     * 잠그기 때문이고, 무엇보다 이 경로는 "이미 결말난 결제" 를 만났을 때 **attempt 도
     * 건드리지 않아야** 하므로 판단 순서 자체가 다르다.
     */
    private fun applyRecheck(
        attemptId: Long,
        target: SubscriptionRenewalOutcome,
        paymentId: Long,
        subscription: Subscription,
        now: LocalDateTime,
    ): RecheckApply {
        if (target == SubscriptionRenewalOutcome.CHARGED) {
            if (!renewalAttemptRepository.resolveReviewOutcome(attemptId, target)) return RecheckApply.LOST_RACE
            applySettlement(subscription, paymentId)
            return RecheckApply.APPLIED
        }

        val payment = paymentRepository.findByIdForUpdate(paymentId) ?: return RecheckApply.PAYMENT_MISSING
        if (payment.status == PaymentStatus.COMPLETED || payment.status == PaymentStatus.REFUNDED) {
            log.info(
                "결제가 이미 확정돼 있어 갱신 원장을 미결제로 바꾸지 않는다. attemptId={} paymentId={} status={}",
                attemptId, paymentId, payment.status,
            )
            return RecheckApply.PAYMENT_ALREADY_SETTLED
        }
        if (!renewalAttemptRepository.resolveReviewOutcome(attemptId, target)) return RecheckApply.LOST_RACE
        if (payment.status == PaymentStatus.PENDING) {
            paymentRepository.update(payment.copy(status = PaymentStatus.FAILED))
        }
        markPastDue(subscription, now, target)
        return RecheckApply.APPLIED
    }

    /** [applyRecheck] 의 결말. 호출자가 사용자 문구로 옮긴다. */
    private enum class RecheckApply {
        APPLIED,

        /** 조건부 갱신에서 졌다. 다른 요청이 먼저 확정했다. */
        LOST_RACE,

        /** 웹훅/환불이 먼저 결말을 냈다. **attempt 도 바꾸지 않았다.** */
        PAYMENT_ALREADY_SETTLED,

        PAYMENT_MISSING,
    }

    // ---- 1단계 ----

    private sealed interface Claim {
        val attemptId: Long

        /** 이번 실행이 새로 잡은 주기. 결제 원장이 함께 만들어졌고 청구로 간다. */
        data class Fresh(override val attemptId: Long, val paymentId: Long) : Claim

        /**
         * 앞선 실행이 결과를 못 채운 주기. 재조회로 간다.
         *
         * [paymentId] 가 null 이면 V103 이전에 만들어진 행이다 — 내부 원장 없이 청구된
         * 주기라 정산할 대상이 없다.
         */
        data class Stale(override val attemptId: Long, val paymentId: Long?) : Claim
    }

    private fun claimPeriod(
        subscription: Subscription,
        subscriptionId: Long,
        periodStart: LocalDateTime,
        now: LocalDateTime,
    ): Claim? {
        val fresh = renewalAttemptRepository.claimPeriod(
            SubscriptionRenewalAttempt(
                subscriptionId = subscriptionId,
                periodStart = periodStart,
                outcome = SubscriptionRenewalOutcome.ATTEMPTED,
                createdAt = now,
            ),
        )
        if (fresh != null) {
            /*
             * **선점이 먼저, 결제 생성이 나중이다.**
             *
             * 결제를 먼저 만들면 선점에 실패했을 때 아무도 가리키지 않는 PENDING 결제가
             * 남고, 그건 고객의 결제 내역에 유령 행으로 보인다. 유니크 인덱스가 먼저
             * 판정하게 한다.
             *
             * 이 두 쓰기는 호출자의 claimTx 안에서 한 커밋으로 끝난다.
             */
            val payment = paymentRepository.save(
                Payment(
                    userId = subscription.userId,
                    type = PaymentType.SUBSCRIPTION,
                    amount = subscription.price,
                    currency = RENEWAL_CURRENCY,
                    status = PaymentStatus.PENDING,
                    pgProvider = "portone",
                    // parts[1]·parts[2] 는 기존 형식 그대로다. parts[0] 으로 갱신을 구분한다.
                    description = "$RENEWAL_DESCRIPTION_PREFIX|${subscription.planType.name}|${subscription.billingCycle.name}",
                ),
            )
            val paymentId = payment.id ?: error("결제 원장을 저장한 뒤 id 를 얻지 못했습니다")
            renewalAttemptRepository.linkPayment(fresh, paymentId)
            return Claim.Fresh(attemptId = fresh, paymentId = paymentId)
        }

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
        return Claim.Stale(existing.id, existing.paymentId)
    }

    // ---- 2단계 ----

    /**
     * 실제 청구. 빌링키가 없으면 **시도하지 않는다.**
     *
     * 현재 결제 UI 가 빌링키를 발급하지 않으므로 기존 구독은 전부 이 경로다.
     */
    private fun charge(subscription: Subscription, internalPaymentId: Long): SubscriptionRenewalOutcome? {
        val billingKey = decryptBillingKey(subscription) ?: return SubscriptionRenewalOutcome.BILLING_KEY_MISSING

        return try {
            val payment = gateway.payWithBillingKey(
                PortOneBillingChargeRequest(
                    // 외부 결제 id 를 `ongo-` 로 통일한다. 웹훅 파서가 이 형식만 읽으므로,
                    // 이렇게 해야 성공·환불 웹훅이 우리 원장에 도달한다.
                    paymentId = externalPaymentId(internalPaymentId),
                    billingKey = billingKey,
                    orderName = "${subscription.planType.displayName} 구독 갱신",
                    customerId = subscription.userId.toString(),
                    amount = subscription.price,
                    storeId = portoneStoreId.takeIf { it.isNotBlank() },
                    channelKey = portoneChannelKey.takeIf { it.isNotBlank() },
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
            reconcile(subscription, internalPaymentId)
        }
    }

    /**
     * 청구하지 않고 **조회만으로** 결말을 짓는다.
     *
     * 재청구는 절대 하지 않는다. 이 경로에 온 주기는 이미 돈이 움직였을 수 있다.
     *
     * @return 확정된 결과. 조회 자체가 실패해 아직 알 수 없으면 null.
     */
    private fun reconcile(
        subscription: Subscription,
        internalPaymentId: Long?,
    ): SubscriptionRenewalOutcome? {
        /*
         * V103 이전에 만들어진 주기는 내부 원장이 없다(payment_id = null). 그때는 외부 id 가
         * `sub-…` 형식이었으므로 그 형식으로 조회한다 — 재청구는 하지 않는다.
         */
        val paymentId = internalPaymentId?.let(::externalPaymentId) ?: legacyPaymentId(subscription)
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

        val judged = judge(subscription, payment)
        /*
         * 내부 원장이 없는데 PG 에는 결제가 있다(V103 이전 주기). 정산할 대상이 없어
         * 기간을 연장할 수도, 실패로 내릴 수도 없다 — 돈은 이미 움직였다.
         * 자동으로 정하지 않고 사람이 확인하게 남긴다.
         */
        if (internalPaymentId == null && judged == SubscriptionRenewalOutcome.CHARGED) {
            log.error(
                "내부 결제 원장 없이 청구된 주기다. 사람이 확인해야 한다. subscriptionId={}",
                subscription.id,
            )
            return SubscriptionRenewalOutcome.NEEDS_REVIEW
        }
        return judged
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
    private fun externalPaymentId(internalPaymentId: Long): String = "ongo-$internalPaymentId"

    /**
     * V103 이전 주기의 외부 결제 id.
     *
     * 그때는 `sub-{id}-renew-{date}` 형식이라 웹훅 파서에 걸리지 않았다. 조회에만 쓰며
     * 새 결제를 이 형식으로 만들지 않는다.
     */
    private fun legacyPaymentId(subscription: Subscription): String {
        val periodStart = subscription.currentPeriodEnd ?: subscription.nextBillingDate
        return "sub-${subscription.id}-renew-${periodStart?.toLocalDate()}"
    }

    // ---- 3단계 ----

    /**
     * @return 이번 실행이 결과를 확정했으면 true. 확정을 미뤘으면 false.
     */
    private fun settle(
        subscription: Subscription,
        claim: Claim,
        outcome: SubscriptionRenewalOutcome,
        now: LocalDateTime,
    ): Boolean {
        val paymentId = claim.internalPaymentId()
        return when (outcome) {
            /*
             * **결과 확정이 먼저다.**
             *
             * 아래 applySettlement 가 예외로 끝나도 attempt 는 ATTEMPTED 로 남아야 다음
             * 실행이 재조회로 복구한다 — 순서를 뒤집으면 정산에 실패한 주기가 확정된 것처럼
             * 남는다. 반대로 여기서 먼저 확정하고 정산이 실패하면 같은 트랜잭션이 함께
             * 롤백된다. `complete` 는 이미 완료된 결제에서 조기 반환하므로 웹훅이 먼저
             * 처리했더라도 기간·크레딧이 두 번 반영되지 않는다.
             */
            SubscriptionRenewalOutcome.CHARGED -> {
                renewalAttemptRepository.completeOutcome(claim.attemptId, outcome)
                applySettlement(subscription, paymentId)
                true
            }

            SubscriptionRenewalOutcome.CHARGE_FAILED ->
                settleChargeFailure(subscription, claim, paymentId, now)

            /*
             * **청구를 시도조차 하지 않았다.** 저장된 결제수단이 없어 외부 호출 전에 끝난
             * 경로이므로, 웹훅이 이 주기의 결제를 완료했을 가능성 자체가 없다. 결과는 PG 가
             * 아니라 우리 설정에 대한 사실이라 결제 원장 상태와 무관하게 확정한다.
             *
             * 그래도 결제 원장이 이미 결말이 나 있으면 [failPayment] 가 false 를 돌려주어
             * 구독을 내리지는 않는다 — 방어선은 그대로 둔다.
             */
            SubscriptionRenewalOutcome.BILLING_KEY_MISSING -> {
                renewalAttemptRepository.completeOutcome(claim.attemptId, outcome)
                if (failPayment(paymentId, subscription)) {
                    markPastDue(subscription, now, outcome)
                }
                true
            }

            /*
             * 돈이 이미 움직였다. 구독을 내리면 결제한 고객의 권한을 뺏는 것이므로
             * 상태를 바꾸지 않고 운영자 확인 대상으로만 남긴다.
             *
             * **결제 원장도 PENDING 으로 둔다.** 승인 여부·금액·통화가 불명이라
             * 실패로 적으면 실제로 빠져나간 돈을 "실패"로 기록하게 된다. 미확정은
             * 미확정으로 남는 편이 정직하다.
             */
            SubscriptionRenewalOutcome.NEEDS_REVIEW -> {
                renewalAttemptRepository.completeOutcome(claim.attemptId, outcome)
                log.error("구독 갱신 확인 필요. 상태를 바꾸지 않는다. subscriptionId={}", subscription.id)
                true
            }

            SubscriptionRenewalOutcome.ATTEMPTED ->
                error("확정되지 않은 결과로 정산할 수 없습니다: subscriptionId=${subscription.id}")
        }
    }

    /**
     * 청구했으나 성립하지 않은 주기를 닫는다. **결제 원장을 잠근 뒤에 판단한다.**
     *
     * ## 왜 확정보다 잠금이 먼저인가
     *
     * 청구 결과 판정(2단계)과 이 블록 사이에 웹훅이 같은 결제를 COMPLETED 로 만들 수 있다.
     * 예전에는 `completeOutcome` 이 먼저였고, 그러면 결제 원장은 성공인데 갱신 원장만
     * 미결제로 확정돼 두 원장이 갈렸다. 그 뒤로는 어느 쪽이 사실인지 코드로 판별할 수 없다.
     *
     * 그래서 이미 결말이 난 결제를 만나면 **attempt 도 건드리지 않고 ATTEMPTED 로 남긴다.**
     * 여기서 CHARGED 로 올리지도 않는다 — 성공 확정은 PG 가 PAID 이고 금액·통화가 일치할
     * 때만 하는 일이고(2단계 [judge]), 이 시점의 우리는 그것을 확인하지 않았다.
     * 다음 실행이 유예를 지나 [reconcile] 로 PG 에 다시 물어 사실을 확정한다.
     *
     * @return 이번 실행이 결과를 확정했으면 true.
     */
    private fun settleChargeFailure(
        subscription: Subscription,
        claim: Claim,
        internalPaymentId: Long?,
        now: LocalDateTime,
    ): Boolean {
        // 레거시 주기(내부 원장 없음)는 잠글 대상이 없다. 기존과 같이 확정하고 내린다.
        val paymentId = internalPaymentId ?: return confirmChargeFailure(subscription, claim, now)

        val payment = paymentRepository.findByIdForUpdate(paymentId)
        if (payment == null) {
            log.warn("갱신 결제 원장을 찾지 못했다. subscriptionId={} paymentId={}", subscription.id, paymentId)
            // 원장이 없으므로 성공을 확인할 방법이 없다. 구독은 보수적으로 실패 처리한다.
            return confirmChargeFailure(subscription, claim, now)
        }
        if (payment.status == PaymentStatus.COMPLETED || payment.status == PaymentStatus.REFUNDED) {
            log.warn(
                "결제가 이미 확정돼 있어 갱신 결과를 미결제로 적지 않는다. 다음 실행이 재조회한다. " +
                    "subscriptionId={} paymentId={} status={}",
                subscription.id, paymentId, payment.status,
            )
            return false
        }

        renewalAttemptRepository.completeOutcome(claim.attemptId, SubscriptionRenewalOutcome.CHARGE_FAILED)
        if (payment.status == PaymentStatus.PENDING) {
            paymentRepository.update(payment.copy(status = PaymentStatus.FAILED))
        }
        markPastDue(subscription, now, SubscriptionRenewalOutcome.CHARGE_FAILED)
        return true
    }

    private fun confirmChargeFailure(subscription: Subscription, claim: Claim, now: LocalDateTime): Boolean {
        renewalAttemptRepository.completeOutcome(claim.attemptId, SubscriptionRenewalOutcome.CHARGE_FAILED)
        markPastDue(subscription, now, SubscriptionRenewalOutcome.CHARGE_FAILED)
        return true
    }

    /**
     * 정산을 **결제 서비스에 위임한다.**
     *
     * 구독 기간과 크레딧 권한을 적용하는 곳은 `completeSubscription` 한 곳뿐이다. 예전에는
     * 여기서 직접 기간을 늘렸는데(extendPeriod), 그러면 웹훅 경로와 규칙이 갈라졌다 —
     * 웹훅은 크레딧을 적용하고 갱신은 적용하지 않았으며, 기준점도 서로 달랐다. 결과가
     * 도착 순서에 따라 달라지는 상태였다.
     *
     * `complete` 는 이미 완료된 결제에서 조기 반환하므로, 웹훅이 먼저 처리했다면 여기서
     * 다시 불러도 기간이 두 번 늘지 않는다.
     *
     * 레거시 주기(내부 원장 없음)는 CHARGED 가 될 수 없다 — reconcile 이 NEEDS_REVIEW 로
     * 바꾸므로 여기 도달하지 않는다.
     */
    private fun applySettlement(subscription: Subscription, internalPaymentId: Long?) {
        val paymentId = internalPaymentId
            ?: error("내부 결제 원장 없이 정산할 수 없습니다: subscriptionId=${subscription.id}")

        paymentService.complete(userId = null, portonePaymentId = externalPaymentId(paymentId))
        log.info("구독 갱신 정산 완료. subscriptionId={} paymentId={}", subscription.id, paymentId)
    }

    private fun Claim.internalPaymentId(): Long? = when (this) {
        is Claim.Fresh -> paymentId
        is Claim.Stale -> paymentId
    }

    /**
     * 청구가 성립하지 않은 주기의 결제 원장을 닫는다.
     *
     * 닫지 않으면 PENDING 이 영구히 남아, 고객의 결제 내역에 **영영 끝나지 않는 결제**가
     * 보인다. 다음 달 갱신이 새 PENDING 을 또 만들면 그 목록이 계속 늘어난다.
     *
     * ## 왜 PENDING 만 바꾸는가
     *
     * 그 사이 웹훅이 도착해 COMPLETED 가 됐을 수 있다. 그걸 FAILED 로 덮으면 실제로
     * 빠져나간 돈을 실패로 기록한다. 조건을 걸어 이미 결말이 난 결제는 건드리지 않는다.
     *
     * ## 왜 잠그는가
     *
     * 읽고-확인하고-쓰는 사이에 웹훅이 같은 행을 COMPLETED 로 바꿀 수 있다. `complete()`
     * 이 쓰는 것과 같은 잠금 조회를 써서 두 경로가 같은 순서로 줄을 서게 한다.
     *
     * 레거시 주기(payment_id = null)는 내부 원장이 없어 닫을 대상도 없다.
     *
     * `subscription`은 선점 시점의 스냅샷이다. 그 뒤 웹훅이 같은 결제를 COMPLETED로
     * 만들었다면 이 스냅샷을 다시 저장하는 순간 연장된 기간과 ACTIVE 상태를 잃는다.
     * 결제 원장을 잠근 결과가 이미 성공이면 실패 전이 자체를 건너뛰어야 한다.
     * REFUNDED도 환불 경로가 구독 상태를 별도로 처리하므로 여기서 PAST_DUE로 바꾸지 않는다.
     */
    private fun failPayment(internalPaymentId: Long?, subscription: Subscription): Boolean {
        val paymentId = internalPaymentId ?: return true

        val payment = paymentRepository.findByIdForUpdate(paymentId)
        if (payment == null) {
            log.warn("갱신 결제 원장을 찾지 못했다. subscriptionId={} paymentId={}", subscription.id, paymentId)
            // 원장이 없으므로 성공을 확인할 방법이 없다. 구독은 보수적으로 실패 처리한다.
            return true
        }
        if (payment.status == PaymentStatus.COMPLETED || payment.status == PaymentStatus.REFUNDED) {
            // 웹훅/환불 경로가 먼저 결말을 냈다. 확정된 결과를 오래된 스냅샷으로 덮지 않는다.
            log.info(
                "갱신 결제가 이미 확정돼 있어 상태를 바꾸지 않는다. paymentId={} status={}",
                paymentId, payment.status,
            )
            return false
        }
        if (payment.status == PaymentStatus.PENDING) {
            paymentRepository.update(payment.copy(status = PaymentStatus.FAILED))
        }
        return true
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

        /**
         * **돈이 나가지 않았음이 확정된** PG 상태.
         *
         * `READY`·`VIRTUAL_ACCOUNT_ISSUED` 처럼 아직 승인으로 바뀔 수 있는 중간 상태는
         * 넣지 않는다. `PARTIAL_CANCELLED` 도 넣지 않는다 — 일부라도 취소됐다는 것은
         * 승인이 있었다는 뜻이라 미결제가 아니다.
         */
        val DEFINITELY_UNPAID_STATUSES = setOf("FAILED", "CANCELLED")

        /** 청구 통화. [PortOneBillingChargeRequest] 기본값과 같아야 한다. */
        const val RENEWAL_CURRENCY = "KRW"

        /**
         * 결제 원장 description 의 첫 칸.
         *
         * `completeSubscription` 은 parts[1]·parts[2] 만 읽으므로 기존 형식과 호환된다.
         * 첫 칸으로 일반 구독 결제와 자동 갱신을 구분한다 — 결제 내역과 대사에서
         * "이건 자동으로 빠져나간 돈"임이 드러나야 한다.
         */
        const val RENEWAL_DESCRIPTION_PREFIX = "SUBSCRIPTION_RENEWAL"

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
