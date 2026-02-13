package com.ongo.api.credit.dto

import com.ongo.common.enums.CreditTransactionType
import java.time.LocalDateTime

data class CreditTransactionResponse(
    val id: Long,
    val type: CreditTransactionType,
    val amount: Int,
    val balanceAfter: Int,
    val feature: String?,
    val createdAt: LocalDateTime?,
)
