package com.ongo.application.coupon

import com.ongo.application.coupon.dto.*
import com.ongo.common.exception.BusinessException
import com.ongo.domain.coupon.Coupon
import com.ongo.domain.coupon.CouponRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 쿠폰 관리(관리자)와 **고객 사용 경로의 차단**.
 *
 * ## 왜 고객 경로를 막는가
 *
 * 쿠폰 할인이 실제 청구에 반영되는 곳이 **한 군데도 없다**.
 * [com.ongo.application.portone.PortOnePaymentService] 는 쿠폰을 읽지 않고 결제 금액을
 * `plan.priceFor(cycle)` 정가로 만든다. 그런데 예전 구현은 "20% 할인"을 계산해 돌려주고,
 * `applyCoupon` 은 `coupon_usages` 행을 만들고 `usedCount` 까지 올렸다.
 *
 * 결과는 **할인을 약속받고 정가로 결제되는 것**이었다. 게다가 쿠폰은 "사용됨"으로 소진돼
 * 나중에 제대로 구현돼도 다시 쓸 수 없었다. 기능이 없는 것보다 나쁘다.
 *
 * 그래서 고객 경로는 아무것도 읽지도 쓰지도 않고 거절한다. 결제에 반영할 수 없는 할인은
 * 검증조차 해서는 안 된다 — "유효합니다"가 곧 약속이기 때문이다.
 *
 * ## 관리자 경로에 대해
 *
 * [createCoupon]·[getCoupons] 는 그대로 둔다. 다만 **여기서 만든 쿠폰은 현재 고객이
 * 사용할 수 없다.** 이 클래스가 사용을 막고 있고, 결제도 쿠폰을 모른다.
 *
 * ## 설계 메모 — 정상 결제와 원자적으로 적용하려면
 *
 * 아래가 모두 갖춰지기 전에는 다시 열지 말 것. 하나라도 빠지면 "할인은 소진됐는데 결제는
 * 정가" 또는 "결제는 할인가인데 쿠폰은 안 쓰인 것으로 남음" 중 하나가 반드시 생긴다.
 *
 * 1. **checkout intent 가 쿠폰 코드를 받는다.** 별도 `/coupons/apply` 호출로는 결제와
 *    묶을 수 없다. 적용 시점과 결제 시점이 갈라지는 순간 원자성이 깨진다.
 * 2. **서버가 재검증하고 금액을 서버에서 계산한다.** 클라이언트가 보낸 할인액을 믿으면
 *    금액 조작이 열린다. `calculateDiscount` 가 PERCENTAGE·FIXED_AMOUNT·FREE_TRIAL_DAYS
 *    를 모두 원값 그대로 돌려주던 문제부터 고쳐야 한다 — 20%·20원·20일이 전부 `20` 이었다.
 * 3. **PG 에 그 할인가로 결제를 만들고, 완료 검증도 그 금액으로 한다.**
 *    `complete()` 의 `verified.amount == payment.amount` 비교 대상이 할인가여야 한다.
 * 4. **예약(reservation) → 결제 완료 시 소비(consumption).** 체크아웃 시점에 사용 가능
 *    수량을 잡아두고, `Transaction.Paid` 웹훅에서만 확정한다. 결제 실패·이탈 시 예약은
 *    만료로 풀려야 한다. 지금처럼 적용 시점에 즉시 소진하면 결제하지 않은 사용자가
 *    쿠폰을 태운다.
 * 5. **`usedCount` 를 원자적으로 다룬다.** 현재의 read-modify-write 는 락이 없어
 *    `maxUses=1` 쿠폰에 동시 요청 N 개가 모두 통과한다. `FOR UPDATE` 또는 조건부 UPDATE
 *    (`WHERE used_count < max_uses`)가 필요하다.
 * 6. **`maxUsesPerUser` 를 실제로 읽는다.** 저장만 하고 읽지 않아, 3회 허용 쿠폰이 1회로
 *    동작하고 있었다.
 *
 * 스키마·결제 계약 변경이 필요하므로 이번 범위에서는 하지 않는다.
 */
@Service
class CouponUseCase(
    private val couponRepository: CouponRepository,
) {

    /**
     * 고객 쿠폰 검증을 거절한다. 항상 던지므로 정상 반환이 없다.
     *
     * 조회조차 하지 않는다. 쿠폰을 찾아 "유효합니다"를 돌려주는 순간 그건 할인 약속이고,
     * 그 약속을 지킬 결제 경로가 없다.
     */
    fun validateCoupon(userId: Long, request: ValidateCouponRequest): Nothing =
        rejectCustomerCouponUse()

    /**
     * 고객 쿠폰 적용을 거절한다. 항상 던지므로 정상 반환이 없다.
     *
     * `saveUsage`·`update` 를 부르지 않는다. 결제에 반영되지 않는 할인으로 쿠폰을
     * 소진시키면, 나중에 제대로 구현돼도 그 사용자는 이미 쓴 것으로 남는다.
     */
    fun applyCoupon(userId: Long, request: ApplyCouponRequest): Nothing =
        rejectCustomerCouponUse()

    private fun rejectCustomerCouponUse(): Nothing = throw BusinessException(
        "COUPON_NOT_APPLICABLE",
        "쿠폰 할인이 현재 결제에 반영되지 않아 쿠폰을 사용할 수 없습니다. " +
            "도움이 필요하시면 고객지원으로 문의해 주세요.",
    )

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

    /*
     * 예전의 validateCouponRules / calculateDiscount 는 지웠다.
     *
     * 남겨두면 "규칙은 이미 있으니 연결만 하면 된다"고 읽히는데, 둘 다 틀려 있었다.
     * calculateDiscount 는 세 할인 타입을 구분하지 않고 discountValue 를 그대로 돌려줬고
     * (20% 와 20원과 20일이 모두 20), 규칙 검사는 maxUsesPerUser 를 읽지 않았으며
     * usedCount 검사는 락 없이 동작했다. 올바른 요구사항은 위 클래스 KDoc 의 설계 메모에
     * 적어 두었다.
     */

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
