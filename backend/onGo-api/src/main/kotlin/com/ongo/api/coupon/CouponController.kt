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

@Tag(name = "쿠폰", description = "쿠폰 검증 및 적용")
@RestController
@RequestMapping("/api/v1/coupons")
class CouponController(
    private val couponUseCase: CouponUseCase,
) {

    @Operation(summary = "쿠폰 검증", description = "쿠폰 코드의 유효성을 검증하고 할인 금액을 미리 계산합니다.")
    @PostMapping("/validate")
    fun validateCoupon(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Valid @RequestBody request: ValidateCouponRequest,
    ): ResponseEntity<ResData<CouponValidationResponse>> =
        ResData.success(couponUseCase.validateCoupon(userId, request))

    @Operation(summary = "쿠폰 적용", description = "쿠폰을 적용합니다.")
    @PostMapping("/apply")
    fun applyCoupon(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Valid @RequestBody request: ApplyCouponRequest,
    ): ResponseEntity<ResData<CouponValidationResponse>> =
        ResData.success(couponUseCase.applyCoupon(userId, request), "쿠폰이 적용되었습니다")
}

@Tag(name = "관리자 - 쿠폰", description = "쿠폰 관리 (관리자 전용)")
@RestController
@RequestMapping("/api/v1/admin/coupons")
class AdminCouponController(
    private val couponUseCase: CouponUseCase,
) {

    @Operation(summary = "쿠폰 생성")
    @PostMapping
    fun createCoupon(@Valid @RequestBody request: CreateCouponRequest): ResponseEntity<ResData<CouponResponse>> =
        ResData.success(couponUseCase.createCoupon(request), "쿠폰이 생성되었습니다")

    @Operation(summary = "쿠폰 목록 조회")
    @GetMapping
    fun getCoupons(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ResData<List<CouponResponse>>> =
        ResData.success(couponUseCase.getCoupons(page, size))
}
