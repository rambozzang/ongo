package com.ongo.application.coupon

import com.ongo.application.coupon.dto.*
import com.ongo.common.exception.BusinessException
import com.ongo.domain.coupon.Coupon
import com.ongo.domain.coupon.CouponRepository
import com.ongo.domain.coupon.CouponUsage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class CouponUseCase(
    private val couponRepository: CouponRepository,
) {

    fun validateCoupon(userId: Long, request: ValidateCouponRequest): CouponValidationResponse {
        val coupon = couponRepository.findByCode(request.code)
            ?: return CouponValidationResponse(valid = false, code = request.code, message = "존재하지 않는 쿠폰입니다")

        val validationError = validateCouponRules(coupon, userId, request.targetPlan, request.billingCycle)
        if (validationError != null) {
            return CouponValidationResponse(valid = false, code = request.code, message = validationError)
        }

        val discount = calculateDiscount(coupon)
        return CouponValidationResponse(
            valid = true,
            code = coupon.code,
            discountType = coupon.discountType,
            discountValue = coupon.discountValue,
            calculatedDiscount = discount,
        )
    }

    @Transactional
    fun applyCoupon(userId: Long, request: ApplyCouponRequest): CouponValidationResponse {
        val coupon = couponRepository.findByCode(request.code)
            ?: throw BusinessException("COUPON_NOT_FOUND", "존재하지 않는 쿠폰입니다")

        val validationError = validateCouponRules(coupon, userId, null, null)
        if (validationError != null) {
            throw BusinessException("COUPON_INVALID", validationError)
        }

        val discount = calculateDiscount(coupon)
        couponRepository.saveUsage(CouponUsage(
            couponId = coupon.id!!,
            userId = userId,
            discountAmount = discount,
            subscriptionId = request.subscriptionId,
        ))
        couponRepository.update(coupon.copy(usedCount = coupon.usedCount + 1))

        return CouponValidationResponse(
            valid = true,
            code = coupon.code,
            discountType = coupon.discountType,
            discountValue = coupon.discountValue,
            calculatedDiscount = discount,
        )
    }

    @Transactional
    fun createCoupon(request: CreateCouponRequest): CouponResponse {
        val coupon = couponRepository.save(Coupon(
            code = request.code.uppercase(),
            description = request.description,
            discountType = request.discountType,
            discountValue = request.discountValue,
            applicablePlans = request.applicablePlans,
            minBillingCycle = request.minBillingCycle,
            maxUses = request.maxUses,
            maxUsesPerUser = request.maxUsesPerUser,
            validFrom = request.validFrom,
            validUntil = request.validUntil,
        ))
        return coupon.toResponse()
    }

    fun getCoupons(page: Int, size: Int): List<CouponResponse> =
        couponRepository.findAll(page, size).map { it.toResponse() }

    private fun validateCouponRules(coupon: Coupon, userId: Long, targetPlan: String?, billingCycle: String?): String? {
        if (!coupon.active) return "비활성화된 쿠폰입니다"
        val now = LocalDateTime.now()
        if (now.isBefore(coupon.validFrom)) return "아직 사용 기간이 아닙니다"
        if (coupon.validUntil != null && now.isAfter(coupon.validUntil)) return "만료된 쿠폰입니다"
        val maxUses = coupon.maxUses
        if (maxUses != null && coupon.usedCount >= maxUses) return "사용 횟수가 초과되었습니다"
        val existingUsage = couponRepository.findUsageByUserAndCoupon(userId, coupon.id!!)
        if (existingUsage != null) return "이미 사용한 쿠폰입니다"
        val applicablePlans = coupon.applicablePlans
        if (targetPlan != null && applicablePlans != null) {
            val plans = applicablePlans.split(",").map { it.trim() }
            if (targetPlan !in plans) return "해당 플랜에 적용할 수 없는 쿠폰입니다"
        }
        if (billingCycle != null && coupon.minBillingCycle != null) {
            if (coupon.minBillingCycle == "YEARLY" && billingCycle != "YEARLY") return "연간 결제에만 적용 가능한 쿠폰입니다"
        }
        return null
    }

    private fun calculateDiscount(coupon: Coupon): Int = when (coupon.discountType) {
        "PERCENTAGE" -> coupon.discountValue
        "FIXED_AMOUNT" -> coupon.discountValue
        "FREE_TRIAL_DAYS" -> coupon.discountValue
        else -> 0
    }

    private fun Coupon.toResponse(): CouponResponse = CouponResponse(
        id = id!!,
        code = code,
        description = description,
        discountType = discountType,
        discountValue = discountValue,
        applicablePlans = applicablePlans,
        maxUses = maxUses,
        usedCount = usedCount,
        active = active,
        validFrom = validFrom,
        validUntil = validUntil,
    )
}
