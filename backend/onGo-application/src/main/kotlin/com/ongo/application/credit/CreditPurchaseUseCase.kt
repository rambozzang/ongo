package com.ongo.application.credit

import com.ongo.common.enums.CreditPackage
import com.ongo.common.enums.PaymentStatus
import com.ongo.common.enums.PaymentType
import com.ongo.common.exception.BusinessException
import com.ongo.common.util.safeValueOfOrThrow
import com.ongo.domain.credit.CreditRepository
import com.ongo.domain.payment.Payment
import com.ongo.domain.payment.PaymentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class CreditPurchaseUseCase(
    private val creditRepository: CreditRepository,
    private val paymentRepository: PaymentRepository,
) {

    @Transactional
    fun purchaseCredits(userId: Long, packageType: String, paymentMethod: String): PurchaseResult {
        // 1. 패키지 유효성 검증
        val creditPackage = safeValueOfOrThrow<CreditPackage>(packageType)

        // 2. 결제 기록 생성 (PENDING)
        val payment = paymentRepository.save(
            Payment(
                userId = userId,
                type = PaymentType.CREDIT,
                amount = creditPackage.price,
                status = PaymentStatus.PENDING,
                paymentMethod = paymentMethod,
                description = "${creditPackage.displayName} (${creditPackage.credits} 크레딧)",
            )
        )

        // 3. 결제는 PENDING 상태로 유지, PG 웹훅에서 COMPLETED 처리 시 크레딧 지급
        //    PG 결제 승인 요청은 클라이언트 → PG SDK에서 처리 후 웹훅으로 결과 수신

        return PurchaseResult(
            transactionId = payment.id!!,
            creditsAdded = creditPackage.credits,
            newBalance = creditRepository.findByUserId(userId)?.balance ?: 0,
            expiresAt = LocalDateTime.now().plusDays(creditPackage.validDays.toLong()),
        )
    }

    fun getPackages(): List<CreditPackageInfo> {
        return CreditPackage.entries.map { pkg ->
            CreditPackageInfo(
                name = pkg.name,
                displayName = pkg.displayName,
                credits = pkg.credits,
                price = pkg.price,
                validDays = pkg.validDays,
                pricePerCredit = pkg.price.toDouble() / pkg.credits,
            )
        }
    }
}

data class PurchaseResult(
    val transactionId: Long,
    val creditsAdded: Int,
    val newBalance: Int,
    val expiresAt: LocalDateTime,
)

data class CreditPackageInfo(
    val name: String,
    val displayName: String,
    val credits: Int,
    val price: Int,
    val validDays: Int,
    val pricePerCredit: Double,
)
