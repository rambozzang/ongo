package com.ongo.domain.credit

import com.ongo.common.enums.CreditTransactionType
import java.time.LocalDateTime

data class AiCreditTransaction(
    val id: Long? = null,
    val userId: Long,
    val type: CreditTransactionType,
    val amount: Int,
    val balanceAfter: Int,
    val feature: String? = null,
    val referenceId: Long? = null,
    val createdAt: LocalDateTime? = null,
)
