package com.ongo.application.coupon

import com.ongo.application.coupon.dto.ApplyCouponRequest
import com.ongo.application.coupon.dto.ValidateCouponRequest
import com.ongo.common.exception.BusinessException
import com.ongo.domain.coupon.Coupon
import com.ongo.domain.coupon.CouponRepository
import com.ongo.domain.coupon.CouponUsage
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class CouponUseCaseTest {

    @MockK
    private lateinit var couponRepository: CouponRepository

    @InjectMockKs
    private lateinit var couponUseCase: CouponUseCase

    private val userId = 1L
    private val now = LocalDateTime.now()

    private fun createValidCoupon(
        id: Long = 10L,
        code: String = "WELCOME20",
        discountType: String = "PERCENTAGE",
        discountValue: Int = 20,
        applicablePlans: String? = null,
        minBillingCycle: String? = null,
        maxUses: Int? = 100,
        usedCount: Int = 5,
        active: Boolean = true,
        validFrom: LocalDateTime = now.minusDays(10),
        validUntil: LocalDateTime? = now.plusDays(30),
    ) = Coupon(
        id = id,
        code = code,
        description = "환영 할인 쿠폰",
        discountType = discountType,
        discountValue = discountValue,
        applicablePlans = applicablePlans,
        minBillingCycle = minBillingCycle,
        maxUses = maxUses,
        usedCount = usedCount,
        active = active,
        validFrom = validFrom,
        validUntil = validUntil,
    )

    // ──────────────────────────────────────────────
    // 1. 유효 쿠폰 검증 성공
    // ──────────────────────────────────────────────
    @Test
    fun `validateCoupon should return valid=true with discount info for valid coupon`() {
        val coupon = createValidCoupon()
        every { couponRepository.findByCode("WELCOME20") } returns coupon
        every { couponRepository.findUsageByUserAndCoupon(userId, 10L) } returns null

        val result = couponUseCase.validateCoupon(userId, ValidateCouponRequest(code = "WELCOME20"))

        assertTrue(result.valid)
        assertEquals("WELCOME20", result.code)
        assertEquals("PERCENTAGE", result.discountType)
        assertEquals(20, result.discountValue)
        assertEquals(20, result.calculatedDiscount)
        assertNull(result.message)
    }

    // ──────────────────────────────────────────────
    // 2. 만료 쿠폰 거부
    // ──────────────────────────────────────────────
    @Test
    fun `validateCoupon should return valid=false for expired coupon`() {
        val coupon = createValidCoupon(validUntil = now.minusDays(1))
        every { couponRepository.findByCode("WELCOME20") } returns coupon

        val result = couponUseCase.validateCoupon(userId, ValidateCouponRequest(code = "WELCOME20"))

        assertFalse(result.valid)
        assertEquals("만료된 쿠폰입니다", result.message)
    }

    // ──────────────────────────────────────────────
    // 3. 비활성 쿠폰 거부
    // ──────────────────────────────────────────────
    @Test
    fun `validateCoupon should return valid=false for inactive coupon`() {
        val coupon = createValidCoupon(active = false)
        every { couponRepository.findByCode("WELCOME20") } returns coupon

        val result = couponUseCase.validateCoupon(userId, ValidateCouponRequest(code = "WELCOME20"))

        assertFalse(result.valid)
        assertEquals("비활성화된 쿠폰입니다", result.message)
    }

    // ──────────────────────────────────────────────
    // 4. 사용 횟수 초과 거부
    // ──────────────────────────────────────────────
    @Test
    fun `validateCoupon should return valid=false when usedCount exceeds maxUses`() {
        val coupon = createValidCoupon(maxUses = 100, usedCount = 100)
        every { couponRepository.findByCode("WELCOME20") } returns coupon

        val result = couponUseCase.validateCoupon(userId, ValidateCouponRequest(code = "WELCOME20"))

        assertFalse(result.valid)
        assertEquals("사용 횟수가 초과되었습니다", result.message)
    }

    // ──────────────────────────────────────────────
    // 5. 이미 사용한 쿠폰 거부
    // ──────────────────────────────────────────────
    @Test
    fun `validateCoupon should return valid=false when user already used coupon`() {
        val coupon = createValidCoupon()
        val existingUsage = CouponUsage(
            id = 1L,
            couponId = 10L,
            userId = userId,
            discountAmount = 20,
        )
        every { couponRepository.findByCode("WELCOME20") } returns coupon
        every { couponRepository.findUsageByUserAndCoupon(userId, 10L) } returns existingUsage

        val result = couponUseCase.validateCoupon(userId, ValidateCouponRequest(code = "WELCOME20"))

        assertFalse(result.valid)
        assertEquals("이미 사용한 쿠폰입니다", result.message)
    }

    // ──────────────────────────────────────────────
    // 6. 적용 불가 플랜 거부
    // ──────────────────────────────────────────────
    @Test
    fun `validateCoupon should return valid=false for non-applicable plan`() {
        val coupon = createValidCoupon(applicablePlans = "PRO,BUSINESS")
        every { couponRepository.findByCode("WELCOME20") } returns coupon
        every { couponRepository.findUsageByUserAndCoupon(userId, 10L) } returns null

        val result = couponUseCase.validateCoupon(
            userId,
            ValidateCouponRequest(code = "WELCOME20", targetPlan = "STARTER"),
        )

        assertFalse(result.valid)
        assertEquals("해당 플랜에 적용할 수 없는 쿠폰입니다", result.message)
    }

    // ──────────────────────────────────────────────
    // 7. 연간 결제 전용 쿠폰 거부
    // ──────────────────────────────────────────────
    @Test
    fun `validateCoupon should return valid=false for yearly-only coupon with monthly billing`() {
        val coupon = createValidCoupon(minBillingCycle = "YEARLY")
        every { couponRepository.findByCode("WELCOME20") } returns coupon
        every { couponRepository.findUsageByUserAndCoupon(userId, 10L) } returns null

        val result = couponUseCase.validateCoupon(
            userId,
            ValidateCouponRequest(code = "WELCOME20", billingCycle = "MONTHLY"),
        )

        assertFalse(result.valid)
        assertEquals("연간 결제에만 적용 가능한 쿠폰입니다", result.message)
    }

    // ──────────────────────────────────────────────
    // 8. 쿠폰 적용 성공
    // ──────────────────────────────────────────────
    @Test
    fun `applyCoupon should save usage and increment usedCount`() {
        val coupon = createValidCoupon()
        every { couponRepository.findByCode("WELCOME20") } returns coupon
        every { couponRepository.findUsageByUserAndCoupon(userId, 10L) } returns null
        every { couponRepository.saveUsage(any()) } answers { firstArg() }
        every { couponRepository.update(any()) } just runs

        val result = couponUseCase.applyCoupon(userId, ApplyCouponRequest(code = "WELCOME20", subscriptionId = 5L))

        assertTrue(result.valid)
        assertEquals("WELCOME20", result.code)
        assertEquals(20, result.calculatedDiscount)

        verify {
            couponRepository.saveUsage(match<CouponUsage> {
                it.couponId == 10L && it.userId == userId && it.discountAmount == 20 && it.subscriptionId == 5L
            })
        }
        verify {
            couponRepository.update(match<Coupon> {
                it.id == 10L && it.usedCount == 6
            })
        }
    }

    // ──────────────────────────────────────────────
    // 9. PERCENTAGE 할인 계산
    // ──────────────────────────────────────────────
    @Test
    fun `validateCoupon should return discountValue as calculatedDiscount for PERCENTAGE type`() {
        val coupon = createValidCoupon(discountType = "PERCENTAGE", discountValue = 15)
        every { couponRepository.findByCode("WELCOME20") } returns coupon
        every { couponRepository.findUsageByUserAndCoupon(userId, 10L) } returns null

        val result = couponUseCase.validateCoupon(userId, ValidateCouponRequest(code = "WELCOME20"))

        assertTrue(result.valid)
        assertEquals("PERCENTAGE", result.discountType)
        assertEquals(15, result.discountValue)
        assertEquals(15, result.calculatedDiscount)
    }

    // ──────────────────────────────────────────────
    // 10. FIXED_AMOUNT 할인 계산
    // ──────────────────────────────────────────────
    @Test
    fun `validateCoupon should return discountValue as calculatedDiscount for FIXED_AMOUNT type`() {
        val coupon = createValidCoupon(discountType = "FIXED_AMOUNT", discountValue = 5000)
        every { couponRepository.findByCode("WELCOME20") } returns coupon
        every { couponRepository.findUsageByUserAndCoupon(userId, 10L) } returns null

        val result = couponUseCase.validateCoupon(userId, ValidateCouponRequest(code = "WELCOME20"))

        assertTrue(result.valid)
        assertEquals("FIXED_AMOUNT", result.discountType)
        assertEquals(5000, result.discountValue)
        assertEquals(5000, result.calculatedDiscount)
    }
}
