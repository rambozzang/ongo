package com.ongo.common.enums

enum class CreditPackage(
    val displayName: String,
    val credits: Int,
    val price: Int,
    val validDays: Int,
) {
    STARTER("스타터 팩", 500, 4_900, 30),
    BASIC("베이직 팩", 1_200, 9_900, 60),
    PRO("프로 팩", 3_000, 19_900, 90),
    BUSINESS("비즈니스 팩", 10_000, 49_900, 180),
}
