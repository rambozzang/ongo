package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.coupon.Coupon
import com.ongo.domain.coupon.CouponRepository
import com.ongo.domain.coupon.CouponUsage
import com.ongo.infrastructure.persistence.jooq.Fields.ACTIVE
import com.ongo.infrastructure.persistence.jooq.Fields.APPLICABLE_PLANS
import com.ongo.infrastructure.persistence.jooq.Fields.APPLIED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.CODE
import com.ongo.infrastructure.persistence.jooq.Fields.COUPON_ID
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DESCRIPTION
import com.ongo.infrastructure.persistence.jooq.Fields.DISCOUNT_AMOUNT
import com.ongo.infrastructure.persistence.jooq.Fields.DISCOUNT_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.DISCOUNT_VALUE
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.MAX_USES
import com.ongo.infrastructure.persistence.jooq.Fields.MAX_USES_PER_USER
import com.ongo.infrastructure.persistence.jooq.Fields.MIN_BILLING_CYCLE
import com.ongo.infrastructure.persistence.jooq.Fields.SUBSCRIPTION_ID
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USED_COUNT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VALID_FROM
import com.ongo.infrastructure.persistence.jooq.Fields.VALID_UNTIL
import com.ongo.infrastructure.persistence.jooq.Tables.COUPONS
import com.ongo.infrastructure.persistence.jooq.Tables.COUPON_USAGES
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class CouponJooqRepository(
    private val dsl: DSLContext,
) : CouponRepository {

    override fun findByCode(code: String): Coupon? =
        dsl.select().from(COUPONS).where(CODE.eq(code)).fetchOne()?.toCoupon()

    override fun findById(id: Long): Coupon? =
        dsl.select().from(COUPONS).where(ID.eq(id)).fetchOne()?.toCoupon()

    override fun save(coupon: Coupon): Coupon {
        val id = dsl.insertInto(COUPONS)
            .set(CODE, coupon.code)
            .set(DESCRIPTION, coupon.description)
            .set(DISCOUNT_TYPE, coupon.discountType)
            .set(DISCOUNT_VALUE, coupon.discountValue)
            .set(APPLICABLE_PLANS, coupon.applicablePlans)
            .set(MIN_BILLING_CYCLE, coupon.minBillingCycle)
            .set(MAX_USES, coupon.maxUses)
            .set(USED_COUNT, coupon.usedCount)
            .set(MAX_USES_PER_USER, coupon.maxUsesPerUser)
            .set(VALID_FROM, coupon.validFrom)
            .set(VALID_UNTIL, coupon.validUntil)
            .set(ACTIVE, coupon.active)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)
        return findById(id)!!
    }

    override fun update(coupon: Coupon) {
        dsl.update(COUPONS)
            .set(DESCRIPTION, coupon.description)
            .set(USED_COUNT, coupon.usedCount)
            .set(ACTIVE, coupon.active)
            .set(VALID_UNTIL, coupon.validUntil)
            .where(ID.eq(coupon.id))
            .execute()
    }

    override fun findUsageByUserAndCoupon(userId: Long, couponId: Long): CouponUsage? =
        dsl.select().from(COUPON_USAGES)
            .where(USER_ID.eq(userId))
            .and(COUPON_ID.eq(couponId))
            .fetchOne()?.toCouponUsage()

    override fun countUsagesByCoupon(couponId: Long): Int =
        dsl.selectCount().from(COUPON_USAGES).where(COUPON_ID.eq(couponId)).fetchOne(0, Int::class.java) ?: 0

    override fun saveUsage(usage: CouponUsage): CouponUsage {
        val id = dsl.insertInto(COUPON_USAGES)
            .set(COUPON_ID, usage.couponId)
            .set(USER_ID, usage.userId)
            .set(DISCOUNT_AMOUNT, usage.discountAmount)
            .set(SUBSCRIPTION_ID, usage.subscriptionId)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)
        return usage.copy(id = id)
    }

    override fun findAll(page: Int, size: Int): List<Coupon> =
        dsl.select().from(COUPONS)
            .orderBy(CREATED_AT.desc())
            .limit(size).offset(page * size)
            .fetch().map { it.toCoupon() }

    private fun Record.toCoupon(): Coupon = Coupon(
        id = get(ID),
        code = get(CODE),
        description = get(DESCRIPTION),
        discountType = get(DISCOUNT_TYPE) ?: "",
        discountValue = get(DISCOUNT_VALUE) ?: 0,
        applicablePlans = get(APPLICABLE_PLANS),
        minBillingCycle = get(MIN_BILLING_CYCLE),
        maxUses = get(MAX_USES),
        usedCount = get(USED_COUNT) ?: 0,
        maxUsesPerUser = get(MAX_USES_PER_USER) ?: 1,
        validFrom = localDateTime(VALID_FROM) ?: java.time.LocalDateTime.now(),
        validUntil = localDateTime(VALID_UNTIL),
        active = get(ACTIVE) ?: true,
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )

    private fun Record.toCouponUsage(): CouponUsage = CouponUsage(
        id = get(ID),
        couponId = get(COUPON_ID),
        userId = get(USER_ID),
        appliedAt = localDateTime(APPLIED_AT) ?: java.time.LocalDateTime.now(),
        discountAmount = get(DISCOUNT_AMOUNT) ?: 0,
        subscriptionId = get(SUBSCRIPTION_ID),
    )
}
