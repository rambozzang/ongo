package com.ongo.domain.coupon

interface CouponRepository {
    fun findByCode(code: String): Coupon?
    fun findById(id: Long): Coupon?
    fun save(coupon: Coupon): Coupon
    fun update(coupon: Coupon)
    fun findUsageByUserAndCoupon(userId: Long, couponId: Long): CouponUsage?
    fun countUsagesByCoupon(couponId: Long): Int
    fun saveUsage(usage: CouponUsage): CouponUsage
    fun findAll(page: Int, size: Int): List<Coupon>
}
