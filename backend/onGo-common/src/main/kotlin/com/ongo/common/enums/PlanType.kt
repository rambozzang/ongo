package com.ongo.common.enums

enum class PlanType(
    val displayName: String,
    val price: Int,
    val maxPlatforms: Int,
    val monthlyUploads: Int,
    val scheduleDays: Int,
    val analyticsDays: Int,
    val storageGB: Int,
    val freeCredits: Int,
    val maxTeamMembers: Int,
) {
    FREE("Free", 0, 1, 5, 0, 7, 1, 30, 0),
    STARTER("Starter", 9_900, 3, 30, 7, 30, 10, 100, 0),
    PRO("Pro", 19_900, 4, 100, 30, 365, 50, 300, 2),
    BUSINESS("Business", 49_900, 4, Int.MAX_VALUE, 90, Int.MAX_VALUE, 200, 1_000, 10),
    ;

    val storageBytes: Long get() = storageGB.toLong() * 1024 * 1024 * 1024
}
