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
     * **`@Transactional`이 반드시 이 메서드에 있어야 한다.** 내부에서 호출하는 `complete()`는
     * 자기호출(self-invocation)이라 프록시를 타지 않아 자체 트랜잭션이 열리지 않는다.
     * 트랜잭션이 없으면 `findByIdForUpdate`의 행 잠금이 SELECT 직후 풀려 중복 지급을 막지 못한다.
     */
    @Transactional
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

        // 멱등 게이트. 처리 대상 이벤트에만 적용해 무시할 이벤트로 행을 남기지 않는다.
        // 서명 검증을 통과했으므로 webhookId는 null이 아니다.
        val eventKey = webhookEventKey(
            webhookId ?: throw PortOneWebhookFormatException("포트원 웹훅에 webhook-id가 없습니다")
        )
        val firstReceipt = webhookEventRepository.saveIfAbsent(
            WebhookEvent(eventId = eventKey, eventType = type, payload = rawBody, status = "PENDING")
        )
        if (!firstReceipt) {
            log.info(
                "이미 수신한 포트원 웹훅이라 건너뛴다. eventId={} eventType={} paymentId={} outcome={}",
                eventKey, type, paymentId, WEBHOOK_OUTCOME_DUPLICATE,
            )
            return
        }

        if (type == WEBHOOK_TYPE_PAID) complete(null, paymentId) else handleCancellation(paymentId)

        // 처리 성공을 이력에 기록한다. PENDING으로 방치하면 성공한 이력이 영구 미처리로 남고
        // idx_webhook_events_status(WHERE status != 'PROCESSED') 부분 인덱스에 계속 쌓인다.
        // 여기까지 왔다는 건 처리가 끝났다는 뜻이다. 중간에 실패하면 예외가 올라가
        // 같은 트랜잭션의 이벤트 삽입까지 함께 롤백되므로 재전송으로 복구된다.
        //
        // 갱신 행수가 0이면 **예외를 던지지 않고 error 로그만 남긴다.** 방금 같은 트랜잭션에서
        // 삽입한 행이라 정상 DB에서는 1이 보장된다. 0은 운영 조건이 아니라 키 불일치 같은
        // 프로그래밍 오류를 뜻하고, 그 경우 모든 웹훅이 같은 지점에서 죽는다. 여기서 예외를
        // 던지면 결제·취소 반영 자체가 영구 실패한다(포트원 재전송도 같은 곳에서 실패).
        // 이력 한 줄이 PENDING으로 남는 쪽이 결제를 잃는 것보다 낫다.
        // 키 불일치는 markProcessed 호출 키를 단언하는 테스트가 이미 막고 있다.
        if (!webhookEventRepository.markProcessed(eventKey, LocalDateTime.now())) {
            // 반복되면 즉시 드러나야 하는 이상 신호다. 알림/집계가 붙을 수 있도록 필드를 고정한다.
            // 로그에 원문 본문·서명 헤더·시크릿은 절대 넣지 않는다. 식별자와 결과만 남긴다.
            // 메트릭을 붙일 때는 eventId 처럼 카디널리티가 무한한 값을 태그로 쓰지 말 것
            // (태그 후보는 eventType, outcome 정도다). 현재 코드베이스에 메트릭 파사드는 없다.
            log.error(
                "포트원 웹훅 이력 갱신 실패. eventId={} eventType={} paymentId={} outcome={}",
                eventKey, type, paymentId, WEBHOOK_OUTCOME_HISTORY_UPDATE_FAILED,
            )
        }
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
        val creditPackage = runCatching { enumValue<CreditPackage>(packageName) }.getOrNull()
        if (creditPackage == null) {
            log.error(
                "크레딧 패키지를 식별할 수 없어 회수를 건너뛴다 [내부 결제={}, description={}]",
                payment.id, payment.description,
            )
            return
        }
        creditService.revokeCredits(payment.userId, creditPackage.credits, "PORTONE_CANCEL_$portonePaymentId")
    }

    /**
     * 구독은 `CANCELLED`로만 표시하고 `planType`과 결제 기간은 유지한다.
     * 이미 낸 기간까지는 권한을 보장해야 하며, 기간이 끝난 뒤 FREE 전환은
     * `BillingScheduler`의 `findCancelledExpired`가 담당한다.
     */
    private fun cancelSubscriptionFor(payment: Payment) {
        val subscription = subscriptionRepository.findByUserId(payment.userId)
        if (subscription == null) {
            log.warn("취소할 구독을 찾을 수 없다 [userId={}]", payment.userId)
            return
        }
        val now = LocalDateTime.now()
        subscriptionRepository.update(
            subscription.copy(
                status = SubscriptionStatus.CANCELLED,
                cancelledAt = now,
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
        val plan = enumValue<PlanType>(planTypeName)
        require(plan != PlanType.FREE) { "무료 플랜은 결제가 필요하지 않습니다" }
        rejectDuplicateSubscriptionPayment(userId, plan)
        val billingCycle = enumValue<BillingCycle>(billingCycleName)
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
    private fun rejectDuplicateSubscriptionPayment(userId: Long, requested: PlanType) {
        val subscription = subscriptionRepository.findByUserId(userId) ?: return
        if (subscription.status != SubscriptionStatus.ACTIVE) return
        if (subscription.planType == PlanType.FREE) return

        val periodEnd = subscription.currentPeriodEnd
        if (periodEnd != null && !periodEnd.isAfter(LocalDateTime.now())) return

        val isUpgrade = requested.price > subscription.planType.price
        if (isUpgrade) return

        throw DuplicateSubscriptionPaymentException(subscription.planType, requested)
    }

    @Transactional
    fun createCreditCheckout(userId: Long, packageName: String): PortOneCheckoutIntent {
        assertPaymentReady()
        val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
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
        val end = if (cycle == BillingCycle.YEARLY) now.plusYears(1) else now.plusMonths(1)
        subscriptionRepository.update(
            subscription.copy(
                planType = plan,
                status = SubscriptionStatus.ACTIVE,
                price = payment.amount,
                billingCycle = cycle,
                currentPeriodStart = now,
                currentPeriodEnd = end,
                nextBillingDate = null,
                pendingPlanType = null,
                storageQuotaLimitBytes = plan.storageBytes,
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

    companion object {
        /** 결제 승인 완료 이벤트. 이 타입만 결제 완료 처리를 수행한다. */
        private const val WEBHOOK_TYPE_PAID = "Transaction.Paid"

        /** 전액 취소. */
        private const val WEBHOOK_TYPE_CANCELLED = "Transaction.Cancelled"

        /** 부분 취소. 이력만 남긴다. */
        private const val WEBHOOK_TYPE_PARTIAL_CANCELLED = "Transaction.PartialCancelled"

        /** 이 목록에 없는 이벤트는 2xx로 조용히 무시한다 (재전송 폭풍 방지). */
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
