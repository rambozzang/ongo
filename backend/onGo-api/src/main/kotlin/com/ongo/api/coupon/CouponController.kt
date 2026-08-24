package com.ongo.api.coupon

import com.ongo.api.config.CurrentUser
import com.ongo.application.coupon.CouponUseCase
import com.ongo.application.coupon.dto.*
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "쿠폰", description = "고객 쿠폰 사용 — 현재 사용할 수 없음")
@RestController
@RequestMapping("/api/v1/coupons")
class CouponController(
    private val couponUseCase: CouponUseCase,
) {

    @Operation(
        summary = "쿠폰 검증 (사용 불가)",
        description = "**항상 400 으로 실패합니다.** 쿠폰 할인을 반영하는 결제 경로가 없어, " +
            "유효성을 알려주는 것 자체가 지킬 수 없는 할인 약속이 되기 때문입니다. " +
            "쿠폰을 조회하지도, 어떤 것도 기록하지도 않습니다. 오류 코드는 COUPON_NOT_APPLICABLE 입니다.",
    )
    @Deprecated("쿠폰 할인이 결제에 반영되지 않는다. 결제 통합 전까지 사용 불가")
    @PostMapping("/validate")
    fun validateCoupon(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Valid @RequestBody request: ValidateCouponRequest,
    ): ResponseEntity<ResData<CouponValidationResponse>> =
        // 항상 던진다(반환 타입 Nothing). 성공 응답을 만드는 코드를 남기지 않는다.
        couponUseCase.validateCoupon(userId, request)

    @Operation(
        summary = "쿠폰 적용 (사용 불가)",
        description = "**항상 400 으로 실패합니다.** 사용 이력 생성·usedCount 증가·구독 변경을 " +
            "일절 하지 않습니다. 결제에 반영되지 않는 할인으로 쿠폰을 소진시키면 나중에 " +
            "정상 구현돼도 그 사용자는 이미 쓴 것으로 남기 때문입니다. " +
            "오류 코드는 COUPON_NOT_APPLICABLE 입니다.",
    )
    @Deprecated("쿠폰 할인이 결제에 반영되지 않는다. 결제 통합 전까지 사용 불가")
    @PostMapping("/apply")
    fun applyCoupon(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Valid @RequestBody request: ApplyCouponRequest,
    ): ResponseEntity<ResData<CouponValidationResponse>> =
        couponUseCase.applyCoupon(userId, request)
}

/**
 * 이 컨트롤러는 이번 변경으로 검증되지 않았다. 쿠폰 행을 만들고 읽을 뿐이며,
 * **여기서 만든 쿠폰을 고객이 사용할 수 있다는 뜻이 아니다** — 고객 경로([CouponController])는
 * 막혀 있고 결제도 쿠폰을 모른다.
 */
@Tag(
    name = "관리자 - 쿠폰",
    description = "쿠폰 레코드 관리 (관리자 전용). 여기서 만든 쿠폰은 현재 고객이 사용할 수 없다.",
)
@RestController
@RequestMapping("/api/v1/admin/coupons")
class AdminCouponController(
    private val couponUseCase: CouponUseCase,
) {

    @Operation(
        summary = "쿠폰 생성",
        description = "쿠폰 레코드를 만듭니다. **고객은 이 쿠폰을 사용할 수 없습니다** — " +
            "쿠폰 할인을 반영하는 결제 경로가 없어 고객 사용 엔드포인트가 막혀 있습니다.",
    )
    @PostMapping
    fun createCoupon(@Valid @RequestBody request: CreateCouponRequest): ResponseEntity<ResData<CouponResponse>> =
        ResData.success(couponUseCase.createCoupon(request), "쿠폰 레코드가 생성되었습니다")

    @Operation(summary = "쿠폰 목록 조회")
    @GetMapping
    fun getCoupons(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ResData<List<CouponResponse>>> =
        ResData.success(couponUseCase.getCoupons(page, size))
}
