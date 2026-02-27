package com.ongo.application.paddle

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.BillingCycle
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
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun handleWebhook(rawBody: String, paddleSignature: String) {
        // 서명 검증
        if (!paddleGateway.verifyWebhookSignature(rawBody, paddleSignature)) {
            throw UnauthorizedException("Paddle 웹훅 서명 검증 실패")
        }

        val event = objectMapper.readValue<Map<String, Any>>(rawBody)
        val eventType = event["event_type"] as? String ?: return
        val data = event["data"] as? Map<*, *> ?: return

        log.info("Paddle 웹훅 수신: eventType=$eventType")

        when (eventType) {
            "subscription.created" -> handleSubscriptionCreated(data)
            "subscription.updated" -> handleSubscriptionUpdated(data)
            "subscription.canceled" -> handleSubscriptionCanceled(data)
            "subscription.past_due" -> handleSubscriptionPastDue(data)
            "transaction.completed" -> handleTransactionCompleted(data)
            "transaction.payment_failed" -> handleTransactionPaymentFailed(data)
            else -> log.info("미처리 Paddle 이벤트: $eventType")
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

        // 크레딧 구매인 경우 크레딧 지급
        val paymentId = payment.id
        if (paymentType == PaymentType.CREDIT && paymentId != null) {
            creditService.addPurchasedCredits(userId, totalAmount, paymentId)
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

    private fun resolvePlanType(data: Map<*, *>): PlanType {
        val items = data["items"] as? List<*> ?: return PlanType.FREE
        val firstItem = items.firstOrNull() as? Map<*, *> ?: return PlanType.FREE
        val price = firstItem["price"] as? Map<*, *>
        val priceId = price?.get("id") as? String ?: return PlanType.FREE

        return when (priceId) {
            paddleGateway.getPriceIdForPlan("STARTER") -> PlanType.STARTER
            paddleGateway.getPriceIdForPlan("PRO") -> PlanType.PRO
            paddleGateway.getPriceIdForPlan("BUSINESS") -> PlanType.BUSINESS
            else -> PlanType.FREE
        }
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
