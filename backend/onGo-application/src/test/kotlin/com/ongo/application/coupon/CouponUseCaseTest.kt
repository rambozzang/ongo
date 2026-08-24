package com.ongo.application.coupon

import com.ongo.application.coupon.dto.ApplyCouponRequest
import com.ongo.application.coupon.dto.ValidateCouponRequest
import com.ongo.common.exception.BusinessException
import com.ongo.domain.coupon.CouponRepository
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * 고객 쿠폰 경로가 **거절되고 아무 흔적도 남기지 않는지** 고정한다.
 *
 * 예전 구현은 "20% 할인"을 계산해 유효하다고 응답했고, 적용 시 `coupon_usages` 행을
 * 만들고 `usedCount` 를 올렸다. 그런데 결제는 쿠폰을 읽지 않아 정가로 청구됐다.
 * 할인은 약속만 되고 쿠폰은 소진되는, 기능이 없는 것보다 나쁜 상태였다.
 */
class CouponUseCaseTest {

    // relaxed 가 아니다. 규정되지 않은 호출이 일어나면 그 자리에서 터진다.
    private val couponRepository = mockk<CouponRepository>()
    private val useCase = CouponUseCase(couponRepository)

    @Test
    fun `쿠폰 검증은 항상 거절한다`() {
        val ex = assertFailsWith<BusinessException> {
            useCase.validateCoupon(1L, ValidateCouponRequest(code = "WELCOME20"))
        }

        assertEquals("COUPON_NOT_APPLICABLE", ex.code)
    }

    @Test
    fun `쿠폰 적용은 항상 거절한다`() {
        val ex = assertFailsWith<BusinessException> {
            useCase.applyCoupon(1L, ApplyCouponRequest(code = "WELCOME20"))
        }

        assertEquals("COUPON_NOT_APPLICABLE", ex.code)
    }

    /**
     * 핵심 보장. 사용 이력도, `usedCount` 증가도, 조회조차 없어야 한다.
     *
     * 조회를 허용하면 "존재하지 않는 쿠폰입니다" 같은 응답으로 코드 존재 여부가 새고,
     * 무엇보다 유효한 쿠폰에 대해 "유효합니다"를 말하고 싶어지는 경로가 다시 열린다.
     */
    @Test
    fun `거절 경로는 쿠폰 저장소를 전혀 건드리지 않는다`() {
        assertFailsWith<BusinessException> {
            useCase.validateCoupon(1L, ValidateCouponRequest(code = "WELCOME20"))
        }
        assertFailsWith<BusinessException> {
            useCase.applyCoupon(1L, ApplyCouponRequest(code = "WELCOME20", subscriptionId = 99))
        }

        verify(exactly = 0) { couponRepository.saveUsage(any()) }
        verify(exactly = 0) { couponRepository.update(any()) }
        verify(exactly = 0) { couponRepository.findByCode(any()) }
        verify(exactly = 0) { couponRepository.findUsageByUserAndCoupon(any(), any()) }
        // 위에 적지 않은 어떤 호출도 없었음을 확인한다.
        confirmVerified(couponRepository)
    }

    /*
     * 문구가 이유와 다음 행동을 담아야 한다. "사용할 수 없습니다"만으로는 쿠폰을 받은
     * 사용자가 어디로 가야 할지 모른다. 그리고 성공을 시사하는 표현이 남으면 안 된다.
     */
    @Test
    fun `거절 안내는 이유와 문의 경로를 밝힌다`() {
        val ex = assertFailsWith<BusinessException> {
            useCase.validateCoupon(1L, ValidateCouponRequest(code = "WELCOME20"))
        }

        assertContains(ex.message!!, "결제에 반영되지 않아")
        assertContains(ex.message!!, "고객지원")
        assertFalse(ex.message!!.contains("적용되었"), "거절 문구가 적용 성공을 시사한다: ${ex.message}")
        assertFalse(ex.message!!.contains("할인이 적용"), "거절 문구가 할인을 약속한다: ${ex.message}")
    }
}
