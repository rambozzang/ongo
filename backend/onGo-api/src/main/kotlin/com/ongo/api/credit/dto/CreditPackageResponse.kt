package com.ongo.api.credit.dto

data class CreditPackageResponse(
    val name: String,
    val displayName: String,
    val credits: Int,
    val price: Int,
    val validDays: Int,
    val pricePerCredit: Double,
)
