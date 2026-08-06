package com.ongo.application.portone

import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.CreditPackage
import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.common.exception.NotFoundException
import com.ongo.common.exception.UnauthorizedException
import com.ongo.domain.payment.Payment
import com.ongo.domain.payment.PaymentRepository
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class PortOnePaymentService(
    private val paymentRepository: PaymentRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository,
    private val creditService: com.ongo.application.credit.CreditService,
    private val gateway: PortOnePaymentGateway,
    private val objectMapper: ObjectMapper,
    @Value("\${payment.portone.store-id:}") private val storeId: String,
    @Value("\${payment.portone.channel-key:}") private val channelKey: String,
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

        if (type == WEBHOOK_TYPE_PAID) complete(null, paymentId) else handleCancellation(paymentId)
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
        val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
        val plan = enumValue<PlanType>(planTypeName)
        require(plan != PlanType.FREE) { "무료 플랜은 결제가 필요하지 않습니다" }
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

    @Transactional
    fun createCreditCheckout(userId: Long, packageName: String): PortOneCheckoutIntent {
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
        return completed.toResult()
    }

    fun configured(): Boolean = storeId.isNotBlank() && channelKey.isNotBlank()

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
