package com.ongo.application.payment

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.credit.CreditService
import com.ongo.application.payment.dto.*
import com.ongo.application.webhook.WebhookInboundGuard
import com.ongo.application.webhook.WebhookInboundOutcome
import com.ongo.common.enums.CreditPackage
import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import com.ongo.common.exception.UnauthorizedException
import com.ongo.domain.payment.Payment
import com.ongo.domain.payment.PaymentRepository
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.common.enums.SubscriptionStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.time.LocalDateTime
import java.util.Base64

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val creditService: CreditService,
    private val subscriptionRepository: SubscriptionRepository,
    private val webhookInboundGuard: WebhookInboundGuard,
    private val objectMapper: ObjectMapper,
    @Value("\${payment.toss.webhook-secret:}") private val tossWebhookSecret: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getHistory(userId: Long, page: Int, size: Int): PaymentHistoryResponse {
        val totalCount = paymentRepository.countByUserId(userId)
        val payments = paymentRepository.findByUserId(userId, page, size)
        return PaymentHistoryResponse(
            payments = payments.map { it.toItem() },
            totalCount = totalCount,
            page = page,
            size = size
        )
    }

    @Transactional
    fun createPayment(userId: Long, type: PaymentType, amount: Int, description: String): Payment {
        val payment = Payment(
            userId = userId,
            type = type,
            amount = amount,
            status = PaymentStatus.PENDING,
            description = description
        )
        return paymentRepository.save(payment)
    }

    /**
     * Toss 결제 웹훅. **PortOne·Paddle 과 같은 계약을 따른다.**
     *
     * ## 이 경로가 뒤처져 있었다
     *
     * 신규 결제는 전부 PortOne 이고 체크아웃 UI 는 Toss 를 부르지 않지만, 이 엔드포인트는
     * 여전히 열려 있고 레거시 결제의 승인·취소 웹훅이 들어올 수 있다. 그런데 형제 두
     * 경로가 각각 고친 결함이 여기만 남아 있었다.
     *
     * - 크레딧 패키지를 식별하지 못하면 **로그만 남기고 계속**했다. 바로 위에서
     *   `COMPLETED` 로 갱신한 결제가 그대로 커밋돼, 돈을 낸 사용자에게 크레딧이 0 인 채
     *   "결제 완료" 만 남았다. 200 을 돌려주니 Toss 도 재시도하지 않는다.
     * - `findById` 라 **행 잠금이 없었다.** 같은 `DONE` 이 동시에 두 번 오면 둘 다
     *   PENDING 을 보고 통과해 크레딧을 두 번 지급한다.
     * - 환불 뒤 늦게 도착한 `DONE` 이 `REFUNDED` 를 `COMPLETED` 로 되살렸다.
     * - `webhook_events` 기록이 없어 중복 탐지·재처리·데드레터가 불가능했다.
     *
     * ## 멱등 키를 어디서 얻는가
     *
     * Toss 에는 `event_id` 헤더가 없다. 그래서 **서명이 실제로 덮는 필드**로만 키를 만든다
     * ([webhookEventKey] 참고). 서명이 덮지 않는 값(예: `paymentKey`)을 키에 넣으면 키의
     * 신뢰도가 서명보다 낮아진다.
     *
     * @throws UnauthorizedException 서명이 없거나 검증에 실패했을 때.
     */
    fun handleWebhook(payload: TossWebhookPayload, signature: String?) {
        // 웹훅 서명 검증 (시크릿이 설정되지 않으면 웹훅 거부)
        if (tossWebhookSecret.isBlank()) {
            throw UnauthorizedException("웹훅 시크릿이 설정되지 않았습니다")
        }
        if (signature.isNullOrBlank()) {
            throw UnauthorizedException("웹훅 서명이 누락되었습니다")
        }
        val expectedSignature = computeHmacSha256(payload.orderId + payload.status + payload.totalAmount, tossWebhookSecret)
        if (signature != expectedSignature) {
            throw UnauthorizedException("웹훅 서명 검증에 실패했습니다")
        }

        log.info("결제 웹훅 수신: orderId=${payload.orderId}, status=${payload.status}")

        val eventKey = webhookEventKey(payload)
        /*
         * 저장하는 본문은 **재직렬화한 JSON** 이다. 원문 바이트가 아니다.
         *
         * 컨트롤러가 이미 역직렬화한 DTO 를 받기 때문인데, 서명 검증이 원문이 아니라
         * 필드 조합(`orderId + status + totalAmount`)으로 이뤄지므로 검증에는 영향이 없다.
         * 이 본문은 데드레터 조사·재처리용 기록이다.
         */
        val outcome = webhookInboundGuard.handle(
            eventId = eventKey,
            eventType = TOSS_EVENT_TYPE_PREFIX + payload.status,
            payload = objectMapper.writeValueAsString(payload),
        ) {
            applyWebhook(payload)
        }
        if (outcome == WebhookInboundOutcome.ALREADY_PROCESSED) {
            log.info(
                "이미 처리한 Toss 웹훅이라 건너뛴다. eventId={} orderId={} status={}",
                eventKey, payload.orderId, payload.status,
            )
        }
    }

    /**
     * 멱등 키. **서명이 덮는 세 필드로만 만든다.**
     *
     * ## 한계 (의도적으로 안전한 쪽)
     *
     * Toss 가 같은 주문에 `DONE` → `CANCELED` → 재승인 `DONE` 을 보내면 두 번째 `DONE` 의
     * 키가 첫 번째와 같아져 **중복으로 간주돼 건너뛴다.** 재승인은 새 주문으로 오는 것이
     * 일반적이라 실무에서 드물고, 틀리더라도 "한 번 더 지급" 이 아니라 "지급하지 않음"
     * 쪽으로 틀린다. 돈이 두 번 나가는 것보다 낫고, 남은 건은 결제 내역 대사로 잡힌다.
     *
     * 반대 방향(환불 뒤 늦은 `DONE`)은 키가 아니라 [applyWebhook] 의 상태 가드가 막는다.
     */
    private fun webhookEventKey(payload: TossWebhookPayload): String =
        "toss:${payload.orderId}:${payload.status}:${payload.totalAmount}"

    /**
     * 실제 반영. **가드가 연 업무 트랜잭션 안에서 실행된다.**
     *
     * 여기서 던지는 예외는 그 트랜잭션을 롤백시키고 가드가 이벤트를 FAILED 로 남긴 뒤
     * 다시 던진다 — 컨트롤러가 2xx 가 아닌 응답을 돌려줘 Toss 가 재전송하고, 반복되면
     * 데드레터로 넘어간다. **조용히 반환하면 그 모든 장치가 무력해진다.**
     */
    private fun applyWebhook(payload: TossWebhookPayload) {
        val paymentId = parsePaymentId(payload.orderId)

        /*
         * **행 잠금으로 읽는다.** 같은 결제에 대한 동시 전달을 직렬화하지 않으면 둘 다
         * 이전 상태를 보고 통과해 크레딧을 두 번 지급한다(PortOne 이 같은 이유로
         * `findByIdForUpdate` 를 쓴다).
         */
        val payment = paymentRepository.findByIdForUpdate(paymentId)
            ?: throw IllegalStateException(
                "Toss 웹훅에 대응하는 결제를 찾을 수 없습니다 [paymentId=$paymentId, orderId=${payload.orderId}]",
            )

        val newStatus = when (payload.status) {
            "DONE" -> PaymentStatus.COMPLETED
            "CANCELED" -> PaymentStatus.REFUNDED
            else -> PaymentStatus.FAILED
        }

        require(payload.totalAmount == payment.amount) {
            "결제 금액이 주문 금액과 일치하지 않습니다"
        }

        /*
         * 환불된 결제를 늦게 도착한 승인 웹훅으로 되살리지 않는다. 되살리면 환불 후에
         * 크레딧·권한이 재활성화된다 — PortOne 과 같은 가드다.
         */
        if (payment.status == PaymentStatus.REFUNDED && newStatus == PaymentStatus.COMPLETED) {
            throw IllegalStateException(
                "환불된 결제는 완료 처리할 수 없습니다 [paymentId=$paymentId]",
            )
        }

        // PG 재전송은 정상이다. 같은 상태로 다시 오면 아무것도 하지 않는다.
        if (payment.status == newStatus) {
            return
        }

        /*
         * **크레딧 지급 근거를 상태 갱신보다 먼저 확정한다.**
         *
         * 순서가 중요하다. 갱신을 먼저 하면 패키지를 못 찾았을 때 "완료인데 크레딧 0" 이
         * 남을 여지가 생긴다. 지금은 예외가 트랜잭션을 통째로 되돌리지만, 근거를 먼저
         * 세워 두면 그 안전성이 순서에 의존하지 않는다.
         *
         * 금액으로 패키지를 역산한다 — payments 에 패키지 식별자가 없기 때문이다
         * (CreditPackage 의 price 는 서로 겹치지 않는다).
         */
        /*
         * 갱신 **전** 상태를 남겨 둔다.
         *
         * 환불 반영(크레딧 회수·구독 해제)은 **실제로 돈이 승인됐던 결제**의 취소에만
         * 적용해야 한다. 결제된 적 없는 `PENDING` 행의 취소로 회수·해제를 하면, 받은 적
         * 없는 것을 빼앗는다. PortOne 도 환불을 `COMPLETED` 에만 적용한다.
         */
        val wasCompleted = payment.status == PaymentStatus.COMPLETED

        val creditPackage = if (payment.type == PaymentType.CREDIT) {
            CreditPackage.entries.find { it.price == payment.amount }
                ?: throw IllegalStateException(
                    "크레딧 패키지를 식별할 수 없어 지급/회수량을 정할 수 없습니다 " +
                        "[paymentId=$paymentId, amount=${payment.amount}]",
                )
        } else {
            null
        }

        paymentRepository.update(payment.copy(
            status = newStatus,
            pgProvider = "toss",
            pgTransactionId = payload.paymentKey,
            paymentMethod = payload.method,
            receiptUrl = payload.receipt?.url
        ))

        when {
            newStatus == PaymentStatus.COMPLETED && creditPackage != null -> {
                log.info("결제 완료 처리: paymentId=$paymentId, amount=${payload.totalAmount}")
                creditService.addPurchasedCredits(payment.userId, creditPackage, paymentId)
            }
            /*
             * **지급된 적 있는 크레딧만 회수한다.**
             *
             * `PENDING` 결제에 취소가 오면 크레딧은 애초에 지급되지 않았다(지급은
             * `COMPLETED` 전이에서만 일어난다). 그런데도 회수하면 **고객이 다른 경로로 산
             * 잔액을 깎는다.** PortOne 도 환불을 `COMPLETED` 에만 적용한다.
             */
            newStatus == PaymentStatus.REFUNDED && creditPackage != null && wasCompleted ->
                creditService.revokeCredits(
                    payment.userId,
                    creditPackage.credits,
                    "PAYMENT_REFUND:$paymentId",
                )
            newStatus == PaymentStatus.REFUNDED &&
                payment.type == PaymentType.SUBSCRIPTION &&
                wasCompleted -> cancelSubscriptionFor(payment)
        }
    }

    /**
     * 환불된 구독 결제의 **구독을 해제한다.** PortOne 전액취소와 같은 계약이다
     * (`PortOnePaymentService.cancelSubscriptionFor`).
     *
     * ## 무엇이 거짓이었나
     *
     * 이 경로는 `PaymentType.CREDIT` 환불만 처리하고 구독은 손대지 않았다. 레거시 Toss
     * 구독 결제가 환불되면 **돈은 돌려주고 유료 권한은 그대로 남았다.**
     *
     * ## planType 을 여기서 내리지 않는 이유
     *
     * 강등은 만료 설계가 소유한다. `SubscriptionJooqRepository.findCancelledExpired` 가
     * `status = CANCELLED AND current_period_end < now` 로 대상을 고르고,
     * `BillingScheduler.downgradeCancelled` 가 `planType`·`users.planType`·크레딧 권한을
     * 함께 FREE 로 내린다. 즉 **여기서 할 일은 `CANCELLED` 로 표시해 그 설계에 넘기는
     * 것**이고, planType 을 직접 건드리면 기간이 남은 구독의 권한을 앞당겨 끊게 된다.
     * PortOne 도 같은 이유로 planType 을 바꾸지 않는다.
     *
     * ## 실제로 승인됐던 결제만 대상이다
     *
     * 결제된 적 없는 `PENDING` 행에 취소가 와도 구독을 해제하면, 돈을 낸 적 없는 사유로
     * 사용 중인 구독이 끊긴다. PortOne 도 환불을 `COMPLETED` 에만 적용한다.
     *
     * @throws IllegalStateException 구독을 찾지 못했을 때. 조용히 넘어가면 환불은 됐는데
     *   권한만 남는다 — 예외를 던져야 가드의 업무 트랜잭션이 롤백되고 재시도·데드레터가
     *   작동한다.
     */
    private fun cancelSubscriptionFor(payment: Payment) {
        val subscription = subscriptionRepository.findByUserId(payment.userId)
            ?: throw IllegalStateException(
                "취소할 구독을 찾을 수 없어 환불을 반영할 수 없습니다 " +
                    "[userId=${payment.userId}, paymentId=${payment.id}]",
            )
        val now = LocalDateTime.now()
        subscriptionRepository.update(
            subscription.copy(
                status = SubscriptionStatus.CANCELLED,
                cancelledAt = now,
                pendingPlanType = null,
                pendingBillingCycle = null,
                updatedAt = now,
            )
        )
    }

    /**
     * 주문 번호에서 내부 결제 식별자를 읽는다. **계약을 정확히 요구하고 아니면 던진다.**
     *
     * 주문 번호는 결제 생성 시 `ongo-{paymentId}` 로 만든다. 예전에는
     * `substringAfter("ongo-")` 로 잘랐는데, 그 함수는 **구분자가 없으면 원문을, 중간에
     * 있으면 그 뒤를** 돌려준다. 그래서 `xongo-1` 같은 값이 `1` 로 읽혀 **남의 결제를
     * 가리킬 수 있었고**, `ongo--5` 는 음수 식별자가 됐다.
     *
     * 서명이 `orderId` 를 덮으므로 임의 위조는 막히지만, 형식 검증을 서명에만 기대면
     * 서명 키를 공유하는 다른 환경이나 향후 서명 범위 변경에서 그대로 뚫린다. 식별자
     * 계약은 여기서 스스로 지킨다.
     */
    private fun parsePaymentId(orderId: String): Long {
        val suffix = orderId.removePrefix(ORDER_ID_PREFIX)
        val paymentId = suffix.toLongOrNull()
        if (!orderId.startsWith(ORDER_ID_PREFIX) || suffix.isBlank() || paymentId == null || paymentId <= 0) {
            throw IllegalStateException(
                "Toss 웹훅의 주문 번호에서 결제 식별자를 읽을 수 없습니다 [orderId=$orderId]",
            )
        }
        return paymentId
    }

    private companion object {
        /** 이벤트 타입 컬럼에 남길 접두사. 어떤 PG 의 어떤 상태였는지 이력에서 바로 보인다. */
        const val TOSS_EVENT_TYPE_PREFIX = "toss.payment."

        /** 결제 생성 시 만드는 주문 번호 접두사. `ongo-{paymentId}` 형식만 받는다. */
        const val ORDER_ID_PREFIX = "ongo-"
    }

    private fun computeHmacSha256(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(data.toByteArray()))
    }

    private fun Payment.toItem(): PaymentItem = PaymentItem(
        id = id!!,
        type = type,
        amount = amount,
        currency = currency,
        status = status,
        description = description,
        receiptUrl = receiptUrl,
        createdAt = createdAt
    )
}
