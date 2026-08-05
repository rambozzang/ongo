package com.ongo.application.portone

import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.CreditPackage
import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.payment.Payment
import com.ongo.domain.payment.PaymentRepository
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.UserRepository
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
    @Value("\${payment.portone.store-id:}") private val storeId: String,
    @Value("\${payment.portone.channel-key:}") private val channelKey: String,
) {

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
        val payment = paymentRepository.findById(internalId)
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

    private fun parseInternalPaymentId(paymentId: String): Long {
        require(paymentId.startsWith("ongo-")) { "유효하지 않은 결제 ID입니다" }
        return paymentId.removePrefix("ongo-").toLongOrNull()
            ?: throw IllegalArgumentException("유효하지 않은 결제 ID입니다")
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String?): T =
        runCatching { enumValueOf<T>(value?.uppercase() ?: "") }
            .getOrElse { throw IllegalArgumentException("유효하지 않은 결제 항목입니다: $value") }

    private fun BillingCycle.displayName() = if (this == BillingCycle.YEARLY) "연간" else "월간"
    private fun Payment.toResult() = PortOnePaymentResult(id = id!!, status = status.name)
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
