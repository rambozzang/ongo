package com.ongo.application.portone

import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.CreditPackage
import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.DuplicateSubscriptionPaymentException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.exception.UnauthorizedException
import com.ongo.domain.payment.Payment
import com.ongo.domain.payment.PaymentRepository
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.UserRepository
import com.ongo.application.webhook.WebhookEventStatus
import com.ongo.application.webhook.WebhookInboundGuard
import com.ongo.application.webhook.WebhookInboundOutcome
import com.ongo.domain.webhook.WebhookEvent
import com.ongo.domain.webhook.WebhookEventRepository
import com.ongo.application.payment.PaymentCompletedEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.LocalDateTime

@Service
class PortOnePaymentService(
    private val paymentRepository: PaymentRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository,
    private val creditService: com.ongo.application.credit.CreditService,
    private val gateway: PortOnePaymentGateway,
    private val webhookEventRepository: WebhookEventRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${payment.portone.store-id:}") private val storeId: String,
    @Value("\${payment.portone.channel-key:}") private val channelKey: String,
    /** 결제 설정 준비 여부. capability 응답과 같은 판정을 쓴다. */
    private val readiness: PortOneReadiness,
    /**
     * 확정된 결제를 퍼널 측정으로 넘기는 통로.
     *
     * 여기서 활동 로그를 **직접 쓰지 않는다.** 이 클래스의 트랜잭션 안에서 기록하면
     * 기록 실패가 결제를 롤백시킨다 — 포트원에서 이미 승인된 결제를 측정 때문에 깨는
     * 셈이다. 이벤트만 발행하고, 커밋 뒤 기록은
     * [com.ongo.application.payment.PaymentActivityListener] 가 별도 트랜잭션에서 맡는다.
     */
    private val eventPublisher: ApplicationEventPublisher,
    /**
     * 수신 기록·행 잠금·실패 기록의 순서를 정한다. Paddle과 공유한다.
     *
     * 수신·실패 기록을 업무 트랜잭션과 **분리해** 남기는 것이 핵심이다. 같은 트랜잭션에
     * 남기면 실패 시 기록까지 함께 롤백되어 재시도 대상도, DEAD_LETTER도, 운영자가 볼
     * 이력도 남지 않는다.
     */
    private val webhookInboundGuard: WebhookInboundGuard,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 포트원 웹훅을 처리한다.
     *
     * 웹훅 본문은 신뢰하지 않는다. 서명을 먼저 검증한 뒤, paymentId로 포트원 API를 재조회해
     * 결제 상태와 금액을 확인한다.
     *
     * 포트원은 결제 실패/빌링키 등 모든 이벤트를 같은 엔드포인트로 보내고 2xx가 아니면
     * 최대 5회 재전송하므로, 처리 대상이 아닌 이벤트는 예외 없이 무시한다.
     *
     * **`@Transactional`을 붙이면 안 된다.** 업무 트랜잭션은 [WebhookInboundGuard]가 열고 닫는다.
     * 여기에 붙이면 그 안의 `TransactionTemplate`이 이 트랜잭션에 참여해 경계가 바깥으로
     * 나가고, 실패 시 행 잠금을 쥔 채로 REQUIRES_NEW 기록이 같은 행을 기다려 **자기 자신과
     * 교착**한다.
     *
     * 중복 지급 방어는 그대로다. `complete()`는 자기호출이라 프록시를 타지 않지만
     * 가드가 연 트랜잭션 안에서 실행되므로 `findByIdForUpdate`의 행 잠금이 업무가 끝날
     * 때까지 유지된다.
     */
    fun handleWebhook(rawBody: String, webhookId: String?, webhookSignature: String?, webhookTimestamp: String?) {
        if (!gateway.verifyWebhookSignature(rawBody, webhookId, webhookSignature, webhookTimestamp)) {
            throw UnauthorizedException("포트원 웹훅 서명 검증 실패")
        }

        val json = runCatching { objectMapper.readTree(rawBody) }.getOrElse {
            throw PortOneWebhookFormatException("포트원 웹훅 본문을 해석할 수 없습니다")
        }
        val type = json.path("type").asText(null)
        if (type !in HANDLED_WEBHOOK_TYPES) {
            log.debug("처리 대상이 아닌 포트원 웹훅 이벤트 무시: type={}", type)
            return
        }

        val paymentId = json.path("data").path("paymentId").asText(null)
            ?: throw PortOneWebhookFormatException("포트원 웹훅에 paymentId가 없습니다")

        // 서명 검증을 통과했으므로 webhookId는 null이 아니다.
        // 처리 대상 이벤트에만 행을 남겨 무시할 이벤트로 테이블을 채우지 않는다.
        val eventKey = webhookEventKey(
            webhookId ?: throw PortOneWebhookFormatException("포트원 웹훅에 webhook-id가 없습니다")
        )

        /*
         * 수신 기록(REQUIRES_NEW) → 행 잠금 → 업무 처리 → 성공/실패 기록의 순서는
         * [WebhookInboundGuard] 가 정한다. Paddle 과 같은 구조이며, 특히 **실패 기록이 행
         * 잠금을 쥔 채 실행되지 않도록** 업무 트랜잭션 경계를 그 안에 둔다.
         */
        val outcome = webhookInboundGuard.handle(eventKey, type, rawBody) {
            dispatchWebhook(type, paymentId)
        }
        if (outcome == WebhookInboundOutcome.ALREADY_PROCESSED) {
            log.info(
                "이미 처리한 포트원 웹훅이라 건너뛴다. eventId={} eventType={} paymentId={} outcome={}",
                eventKey, type, paymentId, WEBHOOK_OUTCOME_DUPLICATE,
            )
        }
    }

    private fun dispatchWebhook(type: String, paymentId: String) {
        if (type == WEBHOOK_TYPE_PAID) complete(null, paymentId) else handleCancellation(paymentId)
    }

    /**
     * 실패한 포트원 웹훅을 재처리한다. [com.ongo.application.portone.PortOneWebhookRetryScheduler] 가 호출한다.
     *
     * ## 서명을 다시 검증하지 않는 이유
     *
     * 서명은 **전달을 인증**한다. 이 행이 존재한다는 것 자체가 수신 시점에 서명 검증을
     * 통과했다는 뜻이고, 서명 헤더는 저장하지 않으며 타임스탬프도 이미 만료됐다.
     *
     * 더 중요한 것은 **저장된 본문을 신뢰하지 않는다**는 점이다. 본문에서 꺼내 쓰는 것은
     * `type` 과 `paymentId` 뿐이고, 돈이 걸린 판정은 전부 포트원 API 재조회 결과로 한다 —
     * `complete` 는 PAID·금액·통화를 대조하고(:395~), `handleCancellation` 은 재조회한 상태가
     * 취소일 때만 반영한다(:181~). 그래서 저장 본문으로 재처리해도 PG 가 말하는 사실 이상으로
     * 반영되지 않는다.
     *
     * **`@Transactional` 이 반드시 있어야 한다.** `complete`/`handleCancellation` 은 자기호출이라
     * 프록시를 타지 않으므로, 없으면 `findByIdForUpdate` 의 행 잠금이 SELECT 직후 풀린다.
     */
    @Transactional
    fun reprocessWebhookEvent(webhookEvent: WebhookEvent) {
        // 인바운드 재전달과 직렬화한다. 잠금을 얻은 뒤 완료돼 있으면 여기서 끝낸다.
        val locked = webhookEventRepository.findByEventIdForUpdate(webhookEvent.eventId)
        if (locked != null && locked.status == WebhookEventStatus.PROCESSED) {
            log.info("재처리 전에 이미 완료된 포트원 웹훅이다. eventId={}", webhookEvent.eventId)
            return
        }

        val json = runCatching { objectMapper.readTree(webhookEvent.payload) }.getOrElse {
            throw PortOneWebhookFormatException("저장된 포트원 웹훅 본문을 해석할 수 없습니다 [eventId=${webhookEvent.eventId}]")
        }
        val type = json.path("type").asText(null)?.takeIf { it.isNotBlank() }
            ?: throw PortOneWebhookFormatException("저장된 포트원 웹훅에 type 이 없습니다 [eventId=${webhookEvent.eventId}]")

        /*
         * **소유권 판정은 `event_type` 컬럼으로, 분기는 본문으로 한다.** 둘이 어긋나면
         * 포트원 소유로 분류돼 뽑힌 행이 실제로는 다른 타입으로 처리된다.
         */
        if (type != webhookEvent.eventType) {
            throw IllegalStateException(
                "저장된 event_type 과 본문이 다르다 " +
                    "[eventId=${webhookEvent.eventId}, 컬럼=${webhookEvent.eventType}, 본문=$type]",
            )
        }
        if (type !in HANDLED_WEBHOOK_TYPES) {
            throw IllegalStateException(
                "포트원이 처리하는 타입이 아니다 [eventId=${webhookEvent.eventId}, type=$type]",
            )
        }

        val paymentId = json.path("data").path("paymentId").asText(null)?.takeIf { it.isNotBlank() }
            ?: throw PortOneWebhookFormatException("저장된 포트원 웹훅에 paymentId 가 없습니다 [eventId=${webhookEvent.eventId}]")

        dispatchWebhook(type, paymentId)
    }

    /**
     * 멱등 키를 만든다. `webhook_events.event_id`는 Paddle과 공유하므로 접두사로 네임스페이스를 나눈다.
     *
     * 컬럼이 `VARCHAR(200)`이라 넘칠 수 있다. 이때 **자르지 않는다.** 자르면 서로 다른 웹훅이
     * 같은 키가 되어 멱등 게이트가 정상 웹훅을 삼킨다. 결정적 해시로 대체해 길이를 보장하면서
     * 같은 webhook-id는 항상 같은 키가 되도록 한다.
     * 폴백 키는 `portone:sha256:` 15자 + SHA-256 hex 64자 = 79자다.
     */
    private fun webhookEventKey(webhookId: String): String {
        val key = WEBHOOK_EVENT_ID_PREFIX + webhookId
        if (key.length <= WEBHOOK_EVENT_ID_MAX_LENGTH) return key

        val digest = MessageDigest.getInstance("SHA-256").digest(webhookId.toByteArray())
        return WEBHOOK_EVENT_ID_PREFIX + "sha256:" + digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * 결제 취소·부분취소 웹훅을 처리한다.
     *
     * 웹훅 본문의 이벤트 타입을 믿지 않고 포트원 API를 재조회한 **실제 상태**로 판단한다.
     * - `CANCELLED`(전액) → 결제를 `REFUNDED`로 바꾸고 크레딧 회수 / 구독 해제
     * - `PARTIAL_CANCELLED`(부분) → 이력만 남긴다. 크레딧·구독·결제 상태를 건드리지 않는다.
     *   부분 금액을 크레딧 수로 환산하면 패키지 단위·반올림·이미 사용한 크레딧 때문에
     *   과다/과소 회수가 발생한다. 정확한 회수량이 원장으로 확정될 때까지 보수적으로 둔다.
     */
    private fun handleCancellation(portonePaymentId: String) {
        val internalId = parseInternalPaymentId(portonePaymentId)
        val payment = paymentRepository.findByIdForUpdate(internalId)
            ?: throw NotFoundException("결제", internalId)

        val verified = gateway.getPayment(portonePaymentId)
        when {
            verified.status.equals(PORTONE_STATUS_CANCELLED, ignoreCase = true) ->
                applyFullCancellation(payment, portonePaymentId)

            verified.status.equals(PORTONE_STATUS_PARTIAL_CANCELLED, ignoreCase = true) ->
                log.warn(
                    "포트원 부분취소 수신 — 이력만 기록하고 크레딧·구독은 유지한다 [paymentId={}, 내부 결제={}]",
                    portonePaymentId, payment.id,
                )

            else -> log.info(
                "취소 웹훅이지만 포트원 상태가 취소가 아니라 반영하지 않는다 [paymentId={}, status={}]",
                portonePaymentId, verified.status,
            )
        }
    }

    private fun applyFullCancellation(payment: Payment, portonePaymentId: String) {
        if (payment.status == PaymentStatus.REFUNDED) {
            log.info("이미 환불 처리된 결제라 건너뛴다 [내부 결제={}]", payment.id)
            return
        }

        /*
         * 결제하지 않은 PENDING 행에 취소 웹훅이 올 수도 있다. 이를 곧바로 REFUNDED 로
         * 바꾸면 고객이 받은 적 없는 크레딧을 회수하거나, 초기 구독을 취소 상태로 만들게
         * 된다. 환불은 실제로 돈이 승인된 COMPLETED 에만 적용한다. PENDING 이면 PG가
         * 명시적으로 취소했다는 사실만 반영해 FAILED 로 닫고 권한은 건드리지 않는다.
         */
        if (payment.status != PaymentStatus.COMPLETED) {
            if (payment.status == PaymentStatus.PENDING) {
                paymentRepository.update(payment.copy(status = PaymentStatus.FAILED))
            }
            log.info(
                "미확정 결제의 취소는 환불로 처리하지 않는다 [내부 결제={} status={} paymentId={}]",
                payment.id, payment.status, portonePaymentId,
            )
            return
        }

        paymentRepository.update(payment.copy(status = PaymentStatus.REFUNDED))

        when (payment.type) {
            PaymentType.CREDIT -> revokeCreditsFor(payment, portonePaymentId)
            PaymentType.SUBSCRIPTION -> cancelSubscriptionFor(payment)
        }
    }

    /**
     * 회수량은 결제 **금액이 아니라 크레딧 수**다.
     * 금액을 그대로 넘기면 (₩9,900 → 9,900 크레딧) 실제 지급량보다 훨씬 많이 회수된다.
     */
    private fun revokeCreditsFor(payment: Payment, portonePaymentId: String) {
        val packageName = payment.description?.split('|')?.getOrNull(1)
        /*
         * **패키지를 식별하지 못하면 예외로 끝낸다 — 바로 위의 REFUNDED 커밋도 함께 롤백된다.**
         *
         * 예전에는 로그만 남기고 넘어갔다. 그러면 결제는 환불로 찍히는데 지급했던 크레딧은
         * 그대로 남는다. 고객은 돈을 돌려받고 크레딧도 계속 쓴다. 웹훅은 성공으로 처리되어
         * `markProcessed` 까지 찍히니 아무도 그 사실을 모른다.
         *
         * 이 상태는 정상 운영에서 나올 수 없다. `completeCredit` 이 description 을 파싱하지
         * 못하면 예외를 던지므로(:461), **COMPLETED 인 크레딧 결제는 반드시 파싱 가능한
         * `CREDIT|<패키지>` description 을 가진다.** 여기서 파싱이 깨졌다는 것은 결제 후에
         * 행이 바뀌었거나 enum 값이 사라졌다는 뜻이고, 회수량을 추측할 근거가 없다.
         * 지어내지 말고 롤백해 포트원 재전송과 사람의 확인 대상으로 남긴다.
         *
         * Paddle 쪽 같은 자리(`PaddleWebhookService.handleTransactionRefunded`)와 계약을
         * 같게 유지한다.
         */
        val creditPackage = runCatching { enumValue<CreditPackage>(packageName) }.getOrNull()
            ?: throw IllegalStateException(
                "크레딧 패키지를 식별할 수 없어 회수량을 정할 수 없습니다 " +
                    "[내부 결제=${payment.id}, paymentId=$portonePaymentId, description=${payment.description}]",
            )
        creditService.revokeCredits(payment.userId, creditPackage.credits, "PORTONE_CANCEL_$portonePaymentId")
    }

    /**
     * 구독은 `CANCELLED`로만 표시하고 `planType`과 결제 기간은 유지한다.
     * 이미 낸 기간까지는 권한을 보장해야 하며, 기간이 끝난 뒤 FREE 전환은
     * `BillingScheduler`의 `findCancelledExpired`가 담당한다.
     */
    private fun cancelSubscriptionFor(payment: Payment) {
        /*
         * **구독이 없으면 예외로 끝낸다 — REFUNDED 커밋도 함께 롤백된다.**
         *
         * "없을 수도 있으니 넘어간다" 가 성립하지 않는다. 여기까지 오는 결제는 반드시
         * `status = COMPLETED` 인 구독 결제인데(:186 가드), 구독 결제가 COMPLETED 가 되려면
         * `completeSubscription` 이 `findByUserId` 로 구독을 찾아야 한다 — 못 찾으면 거기서
         * `NotFoundException` 을 던진다(:471). 따라서 **결제 시점에 구독 행은 반드시
         * 존재했다.** 지금 없다는 것은 그 뒤에 사라졌다는 뜻이다.
         *
         * 그 상태에서 조용히 성공 처리하면 고객은 환불을 받고 `users.planType` 은 유료로
         * 남는다. 돈은 돌려주고 권한은 회수하지 못하는 누수다.
         */
        val subscription = subscriptionRepository.findByUserId(payment.userId)
            ?: throw IllegalStateException(
                "취소할 구독을 찾을 수 없어 환불을 반영할 수 없습니다 " +
                    "[userId=${payment.userId}, 내부 결제=${payment.id}]",
            )
        val now = LocalDateTime.now()
        subscriptionRepository.update(
            subscription.copy(
                status = SubscriptionStatus.CANCELLED,
                cancelledAt = now,
                pendingPlanType = null,
                pendingBillingCycle = null,
                /*
                 * 환불이다 — 이 결제로 생긴 저장공간 권한도 함께 거둔다.
                 *
                 * 남겨 두면 돈은 돌려주고 유료 저장공간은 그대로 쓰게 된다. 비우면
                 * `getEffectiveLimit` 의 fallback 이 **지금 플랜 기준**으로 돌아간다.
                 * `planType` 은 여기서 바꾸지 않는다(이미 낸 기간까지는 보장) — 기간이
                 * 끝나면 `BillingScheduler.downgradeCancelled` 가 FREE 로 내린다.
                 */
                storageQuotaLimitBytes = null,
                updatedAt = now,
            )
        )
    }

    @Transactional
    fun createSubscriptionCheckout(
        userId: Long,
        planTypeName: String,
        billingCycleName: String,
    ): PortOneCheckoutIntent {
        assertPaymentReady()
        val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
        /*
         * **돈이 움직이기 전에** 크레딧 원장이 있는지 본다.
         *
         * completeSubscription 은 마지막에 applyPlanEntitlement 를 부르는데, 원장이 없으면
         * 거기서 예외가 나고 완료 트랜잭션 전체가 롤백된다 — PENDING → COMPLETED 기록까지
         * 함께. 그 시점에는 PG 승인이 이미 끝나 있어 "카드는 빠져나갔는데 우리 쪽 기록은
         * 없는" 상태가 되고, 재시도는 매번 같은 지점에서 실패한다.
         *
         * 여기서 막으면 결제 행도 만들지 않고 결제창도 열리지 않는다. 읽기만 하며 없는
         * 원장을 만들어 주지 않는다 — 근거 없는 숫자를 원장에 넣지 않기 위해서다.
         */
        creditService.ensureAccountPresence(userId)
        val plan = enumValue<PlanType>(planTypeName)
        require(plan != PlanType.FREE) { "무료 플랜은 결제가 필요하지 않습니다" }
        val billingCycle = enumValue<BillingCycle>(billingCycleName)
        rejectDuplicateSubscriptionPayment(userId, plan, billingCycle)
        val amount = plan.priceFor(billingCycle)
        val payment = paymentRepository.save(
            Payment(
                userId = userId,
                type = PaymentType.SUBSCRIPTION,
                amount = amount,
                currency = "KRW",
                status = PaymentStatus.PENDING,
                pgProvider = "portone",
                description = "SUBSCRIPTION|${plan.name}|${billingCycle.name}",
            )
        )
        return intent(payment, user.email, user.name, "${plan.displayName} ${billingCycle.displayName()} 구독")
    }

    /**
     * 이미 이용 중인 유료 구독과 같거나 더 낮은 등급의 결제 intent 생성을 막는다.
     *
     * 온보딩에서 결제를 끝낸 뒤 '이전'으로 3단계에 돌아가 '다음'을 누르면 같은 구독을 한 번 더
     * 결제할 수 있었다. complete()의 멱등성은 paymentId 단위라 새 체크아웃은 별건으로 통과하고,
     * 실제로 카드가 두 번 청구된다. 화면 상태만으로는 새로고침·직접 API 호출을 막지 못하므로
     * 여기서 닫는다.
     *
     * 업그레이드 판정은 `SubscriptionUseCase`와 같은 가격 비교(`요청 플랜 가격 > 현재 플랜 가격`)를
     * 쓴다. 상위 등급 결제는 통과시켜 구독 화면의 업그레이드 흐름을 그대로 둔다.
     *
     * 다음은 전부 정상적인 재결제라 막지 않는다.
     * - ACTIVE 가 아닌 구독: PAST_DUE 재결제, CANCELLED 재가입, TRIALING 유료 전환, PAUSED/SUSPENDED
     * - 현재 플랜이 FREE: 첫 유료 결제
     * - 결제 기간이 이미 끝난 구독: 갱신
     */
    private fun rejectDuplicateSubscriptionPayment(userId: Long, requested: PlanType, requestedCycle: BillingCycle) {
        val subscription = subscriptionRepository.findByUserId(userId) ?: return
        if (subscription.status != SubscriptionStatus.ACTIVE) return
        if (subscription.planType == PlanType.FREE) return

        val periodEnd = subscription.currentPeriodEnd
        if (periodEnd != null && !periodEnd.isAfter(LocalDateTime.now())) return

        val isUpgrade = requested.price > subscription.planType.price
        if (isUpgrade && subscription.billingCycle == BillingCycle.YEARLY && requestedCycle == BillingCycle.MONTHLY) {
            throw BusinessException(
                "PAYMENT_REQUIRED",
                "연간 구독의 플랜 업그레이드는 연간 결제 주기로 진행해 주세요.",
            )
        }
        val isSamePlanCycleChange = requested == subscription.planType &&
            subscription.billingCycle == BillingCycle.MONTHLY &&
            requestedCycle == BillingCycle.YEARLY
        if (isUpgrade || isSamePlanCycleChange) return

        throw DuplicateSubscriptionPaymentException(subscription.planType, requested)
    }

    @Transactional
    fun createCreditCheckout(userId: Long, packageName: String): PortOneCheckoutIntent {
        assertPaymentReady()
        val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
        /*
         * 구독과 같은 이유다. completeCredit → addPurchasedCredits 가 원장을 요구하므로,
         * 없으면 승인 뒤에 롤백돼 구매분이 어디에도 남지 않는다. 결제 행을 만들기 전에 막는다.
         */
        creditService.ensureAccountPresence(userId)
        val creditPackage = enumValue<CreditPackage>(packageName)
        val payment = paymentRepository.save(
            Payment(
                userId = userId,
                type = PaymentType.CREDIT,
                amount = creditPackage.price,
                currency = "KRW",
                status = PaymentStatus.PENDING,
                pgProvider = "portone",
                description = "CREDIT|${creditPackage.name}",
            )
        )
        return intent(payment, user.email, user.name, "${creditPackage.displayName} 크레딧")
    }

    @Transactional
    fun complete(userId: Long?, portonePaymentId: String): PortOnePaymentResult {
        val internalId = parseInternalPaymentId(portonePaymentId)
        // 잠금 조회여야 한다. 잠그지 않으면 동시에 들어온 두 웹훅이 모두 PENDING을 보고 크레딧을 두 번 지급한다.
        val payment = paymentRepository.findByIdForUpdate(internalId)
            ?: throw NotFoundException("결제", internalId)
        if (userId != null && payment.userId != userId) {
            throw IllegalStateException("본인의 결제만 완료할 수 있습니다")
        }
        if (payment.status == PaymentStatus.COMPLETED) return payment.toResult()
        if (payment.status == PaymentStatus.REFUNDED) {
            // 환불된 결제를 늦게 도착한 Paid 콜백으로 되살리면 환불 후 권한이 재활성화된다.
            throw IllegalStateException("환불된 결제는 완료 처리할 수 없습니다")
        }

        val verified = gateway.getPayment(portonePaymentId)
        require(verified.status.equals("PAID", ignoreCase = true)) {
            "포트원 결제가 완료되지 않았습니다: ${verified.status}"
        }
        require(verified.amount == payment.amount) {
            "결제 금액이 일치하지 않습니다"
        }
        require(verified.currency.equals(payment.currency, ignoreCase = true)) {
            "결제 통화가 일치하지 않습니다"
        }

        val completed = payment.copy(
            status = PaymentStatus.COMPLETED,
            pgProvider = "portone",
            pgTransactionId = verified.transactionId ?: verified.paymentId,
            paymentMethod = verified.paymentMethod,
            receiptUrl = verified.receiptUrl,
        )
        paymentRepository.update(completed)
        when (payment.type) {
            PaymentType.CREDIT -> completeCredit(payment)
            PaymentType.SUBSCRIPTION -> completeSubscription(payment)
        }

        /*
         * 여기까지 온 것은 PG 재조회 검증과 권한 반영이 **모두** 성공했다는 뜻이다.
         *
         * 발행 위치가 중요하다.
         * - 위 `when` 이 던지면(예: 크레딧 패키지를 식별할 수 없음) 이 줄에 도달하지
         *   못하므로, 권한이 반영되지 않은 결제가 완료로 기록되지 않는다.
         * - 메서드 앞의 `status == COMPLETED` 조기 반환도 여기 오지 않는다. 재호출이나
         *   중복 웹훅은 추가 이벤트를 만들지 않는다.
         *
         * 발행은 트랜잭션 안이지만 **소비는 커밋 뒤**다(AFTER_COMMIT). 즉 이 트랜잭션이
         * 롤백되면 이벤트도 없던 일이 되고, 커밋되면 기록 실패가 결제에 닿지 못한다.
         */
        eventPublisher.publishEvent(
            PaymentCompletedEvent(
                userId = payment.userId,
                paymentId = payment.id!!,
                type = payment.type,
            ),
        )
        return completed.toResult()
    }

    /**
     * 결제창이 닫히거나 브라우저가 결제 결과를 받지 못한 뒤, 미확정 결제를 안전하게 정리한다.
     *
     * 결제창의 실패 콜백만 믿고 내부 행을 FAILED 로 바꾸면 안 된다. 콜백이 실패한 순간에도
     * PG 승인이 끝났을 수 있기 때문이다. 반드시 PortOne 을 재조회하고, 결제가 없거나
     * 명시적으로 실패·취소된 경우에만 PENDING 을 FAILED 로 닫는다. 조회 자체가 실패하면
     * 아무것도 바꾸지 않고 예외를 올려 웹훅·운영 대사로 복구할 수 있게 한다.
     *
     * PAID 를 발견하면 일반 `complete` 경로로 보내 금액·통화 검증과 권한 반영을 똑같이
     * 적용한다. 따라서 브라우저가 실패를 보고했어도 실제 결제를 놓치지 않는다.
     */
    @Transactional
    fun reconcileCheckout(userId: Long, portonePaymentId: String): PortOnePaymentResult {
        val internalId = parseInternalPaymentId(portonePaymentId)
        val payment = paymentRepository.findByIdForUpdate(internalId)
            ?: throw NotFoundException("결제", internalId)
        if (payment.userId != userId) {
            throw IllegalStateException("본인의 결제만 확인할 수 있습니다")
        }
        if (payment.status == PaymentStatus.COMPLETED || payment.status == PaymentStatus.REFUNDED) {
            return payment.toResult()
        }

        /*
         * 자동 갱신 결제도 같은 내부 원장을 쓰지만 브라우저 체크아웃이 아니다.
         * 사용자가 결제 내역의 `ongo-{id}`를 재조회 API에 보내면, 스케줄러가 아직
         * PortOne 청구를 시작하기 전인 순간에 404를 보고 자동 갱신을 FAILED 로 닫을
         * 수 있다. 그러면 갱신 작업의 10분 유예와 재조회 규칙을 우회하게 된다.
         * 자동 갱신은 스케줄러만 charge/reconcile 하도록 이 브라우저 경로에서 제외한다.
         */
        if (isSubscriptionRenewalPayment(payment)) {
            log.info("자동 갱신 결제는 브라우저 재조회 대상이 아니다 [내부 결제={}].", payment.id)
            return payment.toResult()
        }

        val verified = gateway.findPayment(portonePaymentId)
        if (verified?.status.equals("PAID", ignoreCase = true)) {
            return complete(userId, portonePaymentId)
        }

        val definitelyUnpaid = verified == null || verified.status.uppercase() in DEFINITELY_UNPAID_PORTONE_STATUSES
        if (definitelyUnpaid && payment.status == PaymentStatus.PENDING) {
            return paymentRepository.update(payment.copy(status = PaymentStatus.FAILED)).toResult()
        }

        // READY 등 아직 결말이 아닌 상태는 추측으로 실패 처리하지 않는다.
        return payment.toResult()
    }

    /**
     * 결제 설정이 준비되지 않았으면 **행을 만들기 전에** 막는다.
     *
     * 두 체크아웃 메서드는 intent 를 만들기 전에 PENDING 결제 행을 먼저 저장한다. 설정이
     * 비어 있으면 그 행은 남고 프론트는 빈 storeId 로 SDK 를 열어 원문 오류를 띄웠다.
     * 고객은 원인을 알 수 없는 실패를 보고, DB 에는 아무도 정리하지 않는 고아 행이 쌓였다.
     *
     * 그래서 두 메서드의 **맨 처음**에서 판정한다. 여기서 던지면 저장도 SDK 호출도 없다.
     *
     * 어느 값이 빠졌는지 말하지 않는다. 사용자가 할 수 있는 일이 없고, 설정 상태를
     * 알려줄 이유도 없다.
     */
    private fun assertPaymentReady() {
        if (readiness.isReady()) return
        throw BusinessException(
            "PAYMENT_NOT_AVAILABLE",
            "온라인 결제를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도하거나 고객지원에 문의해 주세요.",
        )
    }

    private fun completeCredit(payment: Payment) {
        val packageName = payment.description?.split('|')?.getOrNull(1)
            ?: throw IllegalStateException("크레딧 결제 정보가 없습니다")
        creditService.addPurchasedCredits(payment.userId, enumValue(packageName), payment.id!!)
    }

    private fun completeSubscription(payment: Payment) {
        val parts = payment.description?.split('|')
            ?: throw IllegalStateException("구독 결제 정보가 없습니다")
        val plan = enumValue<PlanType>(parts.getOrNull(1))
        val cycle = enumValue<BillingCycle>(parts.getOrNull(2))
        val subscription = subscriptionRepository.findByUserId(payment.userId)
            ?: throw NotFoundException("구독", payment.userId)
        val now = LocalDateTime.now()
        val isRenewal = parts.firstOrNull() == "SUBSCRIPTION_RENEWAL"
        /*
         * 자동 갱신은 유예기간 안에서 지금 시각에 완료될 수 있다. 이때 `now + 1개월`을
         * 새 종료일로 쓰면 원래 청구 경계가 결제 지연만큼 뒤로 밀려 고객에게 유예기간을
         * 무료로 더 주고, 다음 청구일도 실제 주기와 어긋난다. 갱신 결제는 이미 저장된
         * 청구 경계를 기준으로 다음 한 주기를 붙인다.
         *
         * 미래의 종료일은 별도 갱신이 이미 반영된 상태일 수 있으므로 기존 보호 규칙으로
         * 그대로 보존한다. 경계가 없는 레거시 행은 추측하지 않고 기존처럼 지금부터 계산한다.
         */
        val renewalBoundary = if (isRenewal) {
            (subscription.currentPeriodEnd ?: subscription.nextBillingDate)
                ?.takeIf { !it.isAfter(now) }
        } else {
            null
        }
        val computedStart = renewalBoundary ?: now
        val computedEnd = if (cycle == BillingCycle.YEARLY) {
            computedStart.plusYears(1)
        } else {
            computedStart.plusMonths(1)
        }
        /*
         * **기간은 뒤로 가지 않는다.**
         *
         * 이 메서드는 구독 기간과 entitlement 를 적용하는 **유일한 곳**이다. 첫 결제 웹훅과
         * 갱신 정산이 모두 여기로 들어오므로, 순서가 뒤바뀌어도 결과가 같아야 한다.
         *
         * 늦게 도착한 웹훅이 이미 연장된 기간을 now+1개월로 되돌리면 고객이 산 기간이
         * 줄어든다. complete() 의 COMPLETED 조기 반환이 대부분 막지만, 그건 같은 결제에만
         * 해당한다. 여기서 한 겹 더 둔다 — 계산값이 기존 종료일보다 이르면 기존 값을 지킨다.
         */
        val existingEnd = subscription.currentPeriodEnd
        val end = if (existingEnd != null && existingEnd.isAfter(computedEnd)) existingEnd else computedEnd
        subscriptionRepository.update(
            subscription.copy(
                planType = plan,
                status = SubscriptionStatus.ACTIVE,
                price = payment.amount,
                billingCycle = cycle,
                // 시작일도 뒤로 가지 않게 종료일과 함께 움직인다.
                currentPeriodStart = if (end == computedEnd) computedStart else (subscription.currentPeriodStart ?: computedStart),
                currentPeriodEnd = end,
                /*
                 * 다음 청구일은 이번 주기의 끝이다.
                 *
                 * 예전에는 null 이었다. findDueForBilling 이 `next_billing_date <= now` 로
                 * 거르므로 null 은 영원히 걸리지 않고, 결제한 고객이 한 번 내고 유료 플랜을
                 * 계속 쓰게 된다. 갱신 스케줄러가 이 값을 보고 주기를 판정한다.
                 */
                nextBillingDate = end,
                pendingPlanType = null,
                pendingBillingCycle = null,
                /*
                 * **플랜 기본 저장공간을 여기에 적지 않는다.**
                 *
                 * 이 컬럼은 `StorageQuotaUseCase.getEffectiveLimit` 에서 플랜을 **무시하는
                 * 절대 오버라이드**로 읽힌다(`limit = 컬럼 ?: plan.storageBytes`). 그래서
                 * 결제할 때마다 플랜 값을 적어 넣으면 두 가지가 동시에 깨진다.
                 *
                 *  - 관리자가 CS 로 올려 준 한도(`AdminUseCase.updateStorageQuota`)가 다음
                 *    결제 한 번에 조용히 사라진다.
                 *  - 하향 경로가 이 값을 지우지 않으면(그리고 지우지 않았다) FREE 로 내려간
                 *    뒤에도 유료 시절 한도가 그대로 남는다 — 결제 없이 유료 저장공간을 쓴다.
                 *
                 * 적지 않아도 유료 한도는 그대로 나온다. 컬럼이 비면 fallback 이 지금 플랜의
                 * `storageBytes` 를 돌려주기 때문이다. 이 컬럼의 뜻은 **관리자 오버라이드
                 * 하나로 좁힌다.**
                 *
                 * ## 다만 낮은 값은 지운다
                 *
                 * 이 변경 전에 결제한 계정에는 **그때 플랜의 값이 남아 있다.** 그대로 두면
                 * STARTER(10GB) 를 쓰던 사용자가 PRO(50GB) 로 올려도 옛 10GB 에 묶인다 —
                 * 돈을 냈는데 산 것을 못 받는, 고치려던 것과 정반대 방향의 결함이다.
                 *
                 * 그래서 **새 플랜 기본값보다 작은 값만** 지운다. 그런 값은 사실상 옛 플랜의
                 * 잔재이며, 설령 관리자가 일부러 낮춰 둔 것이라도 방금 결제한 고객에게
                 * 산 만큼 주는 쪽으로 틀리는 편이 낫다. 더 큰 값(진짜 상향 오버라이드)은
                 * 손대지 않는다.
                 */
                storageQuotaLimitBytes = subscription.storageQuotaLimitBytes
                    ?.takeIf { it > plan.storageBytes },
                paddleSubscriptionId = null,
                cancelledAt = null,
                trialEnd = null,
                trialPlanType = null,
                updatedAt = now,
            )
        )
        val user = userRepository.findById(payment.userId) ?: throw NotFoundException("사용자", payment.userId)
        userRepository.update(user.copy(planType = plan))
        /*
         * 결제 직후 크레딧이 여전히 FREE 기준(30)이면, STARTER 를 산 사용자가 쇼츠 실행
         * 한 번(37)조차 못 돌린다. 구독만 ACTIVE 로 바뀌고 쓸 수 있는 것은 그대로인 상태다.
         *
         * 이 메서드는 호출자의 트랜잭션 안에서 돈다 — 웹훅과 클라이언트 complete 양쪽 모두
         * 결제 멱등 처리를 마친 뒤 한 번만 여기에 도달하므로, 크레딧도 그 횟수만큼만 적용된다.
         */
        creditService.applyPlanEntitlement(payment.userId, plan, reason = "SUBSCRIPTION_PAID")
    }

    private fun intent(payment: Payment, email: String, name: String, orderName: String) =
        PortOneCheckoutIntent(
            paymentId = "ongo-${payment.id}",
            storeId = storeId,
            channelKey = channelKey,
            amount = payment.amount,
            currency = payment.currency,
            orderName = orderName,
            customerEmail = email,
            customerName = name,
        )

    /** 형식이 틀린 결제 ID는 재전송해도 그대로이므로 영구 오류로 분류한다. */
    private fun parseInternalPaymentId(paymentId: String): Long {
        if (!paymentId.startsWith("ongo-")) throw PortOneWebhookFormatException("유효하지 않은 결제 ID입니다")
        return paymentId.removePrefix("ongo-").toLongOrNull()
            ?: throw PortOneWebhookFormatException("유효하지 않은 결제 ID입니다")
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String?): T =
        runCatching { enumValueOf<T>(value?.uppercase() ?: "") }
            .getOrElse { throw IllegalArgumentException("유효하지 않은 결제 항목입니다: $value") }

    private fun BillingCycle.displayName() = if (this == BillingCycle.YEARLY) "연간" else "월간"
    private fun Payment.toResult() = PortOnePaymentResult(id = id!!, status = status.name)

    private fun isSubscriptionRenewalPayment(payment: Payment): Boolean =
        payment.type == PaymentType.SUBSCRIPTION &&
            payment.description?.startsWith("SUBSCRIPTION_RENEWAL|") == true

    companion object {
        /** 결제 승인 완료 이벤트. 이 타입만 결제 완료 처리를 수행한다. */
        private const val WEBHOOK_TYPE_PAID = "Transaction.Paid"

        /** 전액 취소. */
        private const val WEBHOOK_TYPE_CANCELLED = "Transaction.Cancelled"

        /** 부분 취소. 이력만 남긴다. */
        private const val WEBHOOK_TYPE_PARTIAL_CANCELLED = "Transaction.PartialCancelled"

        /** 이 목록에 없는 이벤트는 2xx로 조용히 무시한다 (재전송 폭풍 방지). */
        /**
         * 포트원이 **소유한** 이벤트 타입.
         *
         * `webhook_events` 는 Paddle 과 공유하는 테이블이다. 재시도 스케줄러가 집어든 행을
         * 전부 자기 형식으로 파싱하므로, 이 목록이 곧 소유권 경계다. 비우면 재처리가
         * 멈추고, 넓히면 남의 이벤트를 잘못 처리한다.
         */
        val REPROCESSABLE_EVENT_TYPES: Set<String> get() = HANDLED_WEBHOOK_TYPES

        private val HANDLED_WEBHOOK_TYPES = setOf(
            WEBHOOK_TYPE_PAID,
            WEBHOOK_TYPE_CANCELLED,
            WEBHOOK_TYPE_PARTIAL_CANCELLED,
        )

        private const val PORTONE_STATUS_CANCELLED = "CANCELLED"
        private const val PORTONE_STATUS_PARTIAL_CANCELLED = "PARTIAL_CANCELLED"

        /** `webhook_events`를 Paddle과 공유하므로 네임스페이스를 나눈다. */
        private const val WEBHOOK_EVENT_ID_PREFIX = "portone:"

        /**
         * 웹훅 로그의 `outcome` 값. 알림·집계가 문자열로 걸 수 있게 고정한다.
         * 메트릭 태그로 쓸 수 있는 저카디널리티 값이다(`eventId`는 태그로 쓰지 말 것).
         */
        private const val WEBHOOK_OUTCOME_DUPLICATE = "DUPLICATE_SKIPPED"
        private const val WEBHOOK_OUTCOME_HISTORY_UPDATE_FAILED = "HISTORY_UPDATE_FAILED"

        /** `webhook_events.event_id`는 VARCHAR(200)이다. */
        private const val WEBHOOK_EVENT_ID_MAX_LENGTH = 200

        /** 브라우저가 닫힌 뒤에도 PG 승인으로 바뀔 수 있는 중간 상태는 여기에 넣지 않는다. */
        private val DEFINITELY_UNPAID_PORTONE_STATUSES = setOf("FAILED", "CANCELLED")
    }
}

data class PortOneCheckoutIntent(
    val paymentId: String,
    val storeId: String,
    val channelKey: String,
    val amount: Int,
    val currency: String,
    val orderName: String,
    val customerEmail: String,
    val customerName: String,
)

data class PortOnePaymentResult(val id: Long, val status: String)
