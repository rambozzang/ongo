package com.ongo.api.credit.dto

import java.time.LocalDate

data class CreditBalanceResponse(
    val totalBalance: Int,
    val freeRemaining: Int,
    val freeMonthly: Int,
    val purchasedBalance: Int,
    val freeResetDate: LocalDate,
)
