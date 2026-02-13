package com.ongo.api.credit.dto

import java.time.LocalDateTime

data class PurchaseResultResponse(
    val transactionId: Long,
    val creditsAdded: Int,
    val newBalance: Int,
    val expiresAt: LocalDateTime,
)
