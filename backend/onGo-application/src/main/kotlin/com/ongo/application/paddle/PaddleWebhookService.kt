package com.ongo.application.paddle

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.CreditPackage
import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.common.exception.UnauthorizedException
import com.ongo.domain.payment.Payment
import com.ongo.domain.payment.PaymentRepository
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.UserRepository
import com.ongo.domain.webhook.WebhookEvent
import com.ongo.domain.webhook.WebhookEventRepository
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.OffsetDateTime

@Service
class PaddleWebhookService(
    private val paddleGateway: PaddleGateway,
    private val subscriptionRepository: SubscriptionRepository,
    private val paymentRepository: PaymentRepository,
    private val userRepository: UserRepository,
    private val creditService: CreditService,
    private val objectMapper: ObjectMapper,
    private val webhookEventRepository: WebhookEventRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun handleWebhook(rawBody: String, paddleSignature: String) {
        // 서명 검증
        if (!paddleGateway.verifyWebhookSignature(rawBody, paddleSignature)) {
            throw UnauthorizedException("Paddle 웹훅 서명 검증 실패")
        }

        // 리플레이 공격 방지: 타임스탬프 검증 (5분 허용)
        val ts = paddleSignature.split(";")
            .associate { it.split("=", limit = 2).let { p -> p[0] to p.getOrElse(1) { "" } } }["ts"]
        if (ts != null) {
            val webhookTime = try { ts.toLong() } catch (_: NumberFormatException) { 0L }
            val now = System.currentTimeMillis() / 1000
            val toleranceSeconds = 300L // 5분
            if (kotlin.math.abs(now - webhookTime) > toleranceSeconds) {
                log.warn("Paddle 웹훅 타임스탬프 만료: ts=$ts, now=$now, diff=${now - webhookTime}s")
                throw UnauthorizedException("Paddle 웹훅 타임스탬프 만료")
            }
        }

        val event = objectMapper.readValue<Map<String, Any>>(rawBody)
        val eventType = event["event_type"] as? String ?: return
        val eventId = event["event_id"] as? String ?: UUID.randomUUID().toString()
        val data = event["data"] as? Map<*, *> ?: return

        log.info("Paddle 웹훅 수신: eventType=$eventType, eventId=$eventId")

        // 멱등성 검사: 이미 처리된 이벤트 스킵
        val existing = webhookEventRepository.findByEventId(eventId)
        if (existing != null && existing.status == "PROCESSED") {
            log.info("이미 처리된 웹훅 이벤트: eventId=$eventId")
            return
        }

        // 이벤트 저장 (PENDING)
        val webhookEvent = if (existing != null) {
            existing
        } else {
            webhookEventRepository.save(WebhookEvent(
                eventId = eventId,
                eventType = eventType,
                payload = rawBody,
                status = "PENDING",
            ))
        }

        try {
            when (eventType) {
                "subscription.created" -> handleSubscriptionCreated(data)
                "subscription.updated" -> handleSubscriptionUpdated(data)
                "subscription.canceled" -> handleSubscriptionCanceled(data)
                "subscription.past_due" -> handleSubscriptionPastDue(data)
                "transaction.completed" -> handleTransactionCompleted(data)
                "transaction.payment_failed" -> handleTransactionPaymentFailed(data)
                "transaction.refunded" -> handleTransactionRefunded(data)
                else -> log.info("미처리 Paddle 이벤트: $eventType")
            }
            // 처리 성공
            webhookEventRepository.update(webhookEvent.copy(
                status = "PROCESSED",
                processedAt = LocalDateTime.now(),
            ))
        } catch (e: Exception) {
            // 처리 실패: 재시도 가능하도록 저장
            val retryCount = webhookEvent.retryCount + 1
            val nextRetry = LocalDateTime.now().plusMinutes((1L shl minOf(retryCount, 5)).toLong())
            webhookEventRepository.update(webhookEvent.copy(
                status = "FAILED",
                retryCount = retryCount,
                nextRetryAt = nextRetry,
                errorMessage = e.message?.take(500),
            ))
            throw e
        }
    }

    fun reprocessWebhookEvent(webhookEvent: WebhookEvent) {
        val event = objectMapper.readValue<Map<String, Any>>(webhookEvent.payload)
        val eventType = event["event_type"] as? String ?: return
        val data = event["data"] as? Map<*, *> ?: return
        when (eventType) {
            "subscription.created" -> handleSubscriptionCreated(data)
            "subscription.updated" -> handleSubscriptionUpdated(data)
            "subscription.canceled" -> handleSubscriptionCanceled(data)
            "subscription.past_due" -> handleSubscriptionPastDue(data)
            "transaction.completed" -> handleTransactionCompleted(data)
            "transaction.payment_failed" -> handleTransactionPaymentFailed(data)
            "transaction.refunded" -> handleTransactionRefunded(data)
        }
    }

    private fun handleSubscriptionCreated(data: Map<*, *>) {
        val paddleSubId = data["id"] as? String ?: return
        val customerId = data["customer_id"] as? String ?: return
        val customData = data["custom_data"] as? Map<*, *>
        val userId = (customData?.get("user_id") as? Number)?.toLong() ?: return

        log.info("Paddle 구독 생성: paddleSubId=$paddleSubId, userId=$userId")

        val planType = resolvePlanType(data)
        val billingPeriod = data["current_billing_period"] as? Map<*, *>
        val periodStart = parseDateTime(billingPeriod?.get("starts_at") as? String)
        val periodEnd = parseDateTime(billingPeriod?.get("ends_at") as? String)
        val nextBilledAt = parseDateTime(data["next_billed_at"] as? String)

        // 기존 구독 업데이트
        val existing = subscriptionRepository.findByUserId(userId)
        if (existing != null) {
            subscriptionRepository.update(existing.copy(
                planType = planType,
                status = SubscriptionStatus.ACTIVE,
                price = planType.price,
                billingCycle = BillingCycle.MONTHLY,
                currentPeriodStart = periodStart,
                currentPeriodEnd = periodEnd,
                nextBillingDate = nextBilledAt,
                paddleSubscriptionId = paddleSubId,
                paddleCustomerId = customerId,
                pendingPlanType = null,
                updatedAt = LocalDateTime.now(),
            ))
        } else {
            subscriptionRepository.save(Subscription(
                userId = userId,
                planType = planType,
                status = SubscriptionStatus.ACTIVE,
                price = planType.price,
                billingCycle = BillingCycle.MONTHLY,
                currentPeriodStart = periodStart,
                currentPeriodEnd = periodEnd,
                nextBillingDate = nextBilledAt,
                paddleSubscriptionId = paddleSubId,
                paddleCustomerId = customerId,
            ))
        }

        // 사용자 planType 업데이트
        val user = userRepository.findById(userId)
        if (user != null) {
            userRepository.update(user.copy(planType = planType, paddleCustomerId = customerId))
        }
    }

    private fun handleSubscriptionUpdated(data: Map<*, *>) {
        val paddleSubId = data["id"] as? String ?: return
        log.info("Paddle 구독 업데이트: paddleSubId=$paddleSubId")

        val subscription = subscriptionRepository.findByPaddleSubscriptionId(paddleSubId) ?: run {
            log.warn("Paddle 구독 미발견: $paddleSubId")
            return
        }

        val newPlanType = resolvePlanType(data)
        val status = data["status"] as? String
        val billingPeriod = data["current_billing_period"] as? Map<*, *>
        val periodStart = parseDateTime(billingPeriod?.get("starts_at") as? String)
        val periodEnd = parseDateTime(billingPeriod?.get("ends_at") as? String)
        val nextBilledAt = parseDateTime(data["next_billed_at"] as? String)

        val newStatus = when (status) {
            "active" -> SubscriptionStatus.ACTIVE
            "past_due" -> SubscriptionStatus.PAST_DUE
            "canceled" -> SubscriptionStatus.CANCELLED
            else -> subscription.status
        }

        subscriptionRepository.update(subscription.copy(
            planType = newPlanType,
            status = newStatus,
            price = newPlanType.price,
            currentPeriodStart = periodStart ?: subscription.currentPeriodStart,
            currentPeriodEnd = periodEnd ?: subscription.currentPeriodEnd,
            nextBillingDate = nextBilledAt ?: subscription.nextBillingDate,
            pendingPlanType = null,
            updatedAt = LocalDateTime.now(),
        ))

        // 사용자 planType 동기화
        val user = userRepository.findById(subscription.userId)
        if (user != null && newStatus == SubscriptionStatus.ACTIVE) {
            userRepository.update(user.copy(planType = newPlanType))
        }
    }

    private fun handleSubscriptionCanceled(data: Map<*, *>) {
        val paddleSubId = data["id"] as? String ?: return
        log.info("Paddle 구독 취소: paddleSubId=$paddleSubId")

        val subscription = subscriptionRepository.findByPaddleSubscriptionId(paddleSubId) ?: return

        subscriptionRepository.update(subscription.copy(
            status = SubscriptionStatus.CANCELLED,
            cancelledAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        ))
    }

    private fun handleSubscriptionPastDue(data: Map<*, *>) {
        val paddleSubId = data["id"] as? String ?: return
        log.info("Paddle 구독 연체: paddleSubId=$paddleSubId")

        val subscription = subscriptionRepository.findByPaddleSubscriptionId(paddleSubId) ?: return

        subscriptionRepository.update(subscription.copy(
            status = SubscriptionStatus.PAST_DUE,
            updatedAt = LocalDateTime.now(),
        ))
    }

    private fun handleTransactionCompleted(data: Map<*, *>) {
        val transactionId = data["id"] as? String ?: return
        val customData = data["custom_data"] as? Map<*, *>
        val userId = (customData?.get("user_id") as? Number)?.toLong()

        log.info("Paddle 트랜잭션 완료: transactionId=$transactionId, userId=$userId")

        if (userId == null) {
            log.warn("트랜잭션에 user_id가 없습니다: $transactionId")
            return
        }

        // 중복 처리 방지
        if (paymentRepository.findByPaddleTransactionId(transactionId) != null) {
            log.info("이미 처리된 트랜잭션: $transactionId")
            return
        }

        val details = data["details"] as? Map<*, *>
        val totals = details?.get("totals") as? Map<*, *>
        val totalAmount = (totals?.get("total") as? String)?.toIntOrNull() ?: 0

        // 인보이스 URL 조회
        val invoiceUrl = paddleGateway.getTransactionInvoice(transactionId)

        // 결제 유형 판별 (구독 vs 크레딧)
        val subscriptionId = data["subscription_id"] as? String
        val paymentType = if (subscriptionId != null) PaymentType.SUBSCRIPTION else PaymentType.CREDIT

        // Payment 기록 저장
        val payment = paymentRepository.save(Payment(
            userId = userId,
            type = paymentType,
            amount = totalAmount,
            currency = (totals?.get("currency_code") as? String) ?: "KRW",
            status = PaymentStatus.COMPLETED,
            pgProvider = "paddle",
            pgTransactionId = transactionId,
            paymentMethod = resolvePaymentMethod(data),
            receiptUrl = invoiceUrl,
            paddleTransactionId = transactionId,
            paddleInvoiceUrl = invoiceUrl,
            description = if (paymentType == PaymentType.CREDIT) "AI 크레딧 구매" else "구독 결제",
        ))

        // 크레딧 구매인 경우 크레딧 지급.
        // 결제 금액을 크레딧 수로 넘기던 버그가 있었다(₩49,900 결제 -> 49,900 크레딧 시도).
        // payments 에 패키지 식별자가 없으므로 결제 금액으로 패키지를 역산한다
        // (CreditPackage 의 price 는 서로 겹치지 않는다).
        val paymentId = payment.id
        if (paymentType == PaymentType.CREDIT && paymentId != null) {
            val creditPackage = CreditPackage.entries.find { it.price == totalAmount }
            if (creditPackage == null) {
                log.error("크레딧 패키지를 식별할 수 없어 지급을 건너뜁니다 [paymentId=$paymentId, amount=$totalAmount]")
            } else {
                creditService.addPurchasedCredits(userId, creditPackage, paymentId)
            }
        }
    }

    private fun handleTransactionPaymentFailed(data: Map<*, *>) {
        val transactionId = data["id"] as? String ?: return
        val customData = data["custom_data"] as? Map<*, *>
        val userId = (customData?.get("user_id") as? Number)?.toLong() ?: return

        log.warn("Paddle 결제 실패: transactionId=$transactionId, userId=$userId")

        paymentRepository.save(Payment(
            userId = userId,
            type = PaymentType.SUBSCRIPTION,
            amount = 0,
            status = PaymentStatus.FAILED,
            pgProvider = "paddle",
            pgTransactionId = transactionId,
            paddleTransactionId = transactionId,
            description = "결제 실패",
        ))
    }

    private fun handleTransactionRefunded(data: Map<*, *>) {
        val transactionId = data["id"] as? String ?: return
        log.info("Paddle 환불 처리: transactionId=$transactionId")

        val payment = paymentRepository.findByPaddleTransactionId(transactionId) ?: run {
            log.warn("환불 대상 결제를 찾을 수 없음: $transactionId")
            return
        }

        paymentRepository.update(payment.copy(status = PaymentStatus.REFUNDED))

        // 회수량은 결제 금액이 아니라 크레딧 수다. 금액을 그대로 넘기면
        // (₩4,900 -> 4900 크레딧) 실제 지급량보다 훨씬 많이 회수된다.
        if (payment.type == PaymentType.CREDIT) {
            val creditPackage = CreditPackage.entries.find { it.price == payment.amount }
            if (creditPackage == null) {
                log.error("크레딧 패키지를 식별할 수 없어 회수를 건너뜁니다 [paymentId=${payment.id}, amount=${payment.amount}]")
            } else {
                creditService.revokeCredits(payment.userId, creditPackage.credits, "REFUND_$transactionId")
            }
        }
    }

    private fun resolvePlanType(data: Map<*, *>): PlanType {
        val items = data["items"] as? List<*> ?: return PlanType.FREE
        val firstItem = items.firstOrNull() as? Map<*, *> ?: return PlanType.FREE
        val price = firstItem["price"] as? Map<*, *>
        val priceId = price?.get("id") as? String ?: return PlanType.FREE

        // 월간·연간 가격 ID 를 모두 대조해야 한다. 예전에는 기본값(MONTHLY)만 비교해서
        // 연간 구독으로 결제한 사용자의 price id 가 어디에도 걸리지 않고 else 로 떨어졌고,
        // 결제가 성공했는데도 플랜이 FREE 로 강등됐다.
        val planType = PlanType.entries.firstOrNull { plan ->
            priceId == paddleGateway.getPriceIdForPlan(plan.name, "MONTHLY") ||
                priceId == paddleGateway.getPriceIdForPlan(plan.name, "YEARLY")
        }
        if (planType == null) {
            log.warn("알 수 없는 Paddle price id 라 플랜을 판별하지 못했습니다 [priceId={}]", priceId)
            return PlanType.FREE
        }
        return planType
    }

    private fun resolvePaymentMethod(data: Map<*, *>): String? {
        val payments = data["payments"] as? List<*> ?: return null
        val firstPayment = payments.firstOrNull() as? Map<*, *> ?: return null
        val methodDetails = firstPayment["method_details"] as? Map<*, *>
        return methodDetails?.get("type") as? String
    }

    private fun parseDateTime(dateStr: String?): LocalDateTime? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(dateStr).toLocalDateTime()
        } catch (_: Exception) {
            null
        }
    }
}
