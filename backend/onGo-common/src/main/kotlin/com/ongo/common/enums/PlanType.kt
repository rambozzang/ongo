package com.ongo.common.enums

enum class PlanType(
    val displayName: String,
    val price: Int,
    val yearlyPrice: Int,
    val maxPlatforms: Int,
    val monthlyUploads: Int,
    val scheduleDays: Int,
    val analyticsDays: Int,
    val storageGB: Int,
    val freeCredits: Int,
    val maxTeamMembers: Int,
) {
    FREE("Free", 0, 0, 1, 5, 0, 7, 1, 30, 0),
    STARTER("Starter", 9_900, 99_000, 3, 30, 7, 30, 10, 100, 0),
    PRO("Pro", 19_900, 199_000, 4, 100, 30, 365, 50, 300, 2),
    BUSINESS("Business", 49_900, 499_000, 4, Int.MAX_VALUE, 90, Int.MAX_VALUE, 200, 1_000, 10),
    ;

    val storageBytes: Long get() = storageGB.toLong() * 1024 * 1024 * 1024

    /** 주어진 빌링 주기에 따른 가격 반환 */
    fun priceFor(cycle: com.ongo.common.enums.BillingCycle): Int = when (cycle) {
        com.ongo.common.enums.BillingCycle.MONTHLY -> price
        com.ongo.common.enums.BillingCycle.YEARLY -> yearlyPrice
    }
}
