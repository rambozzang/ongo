package com.ongo.domain.event

import com.ongo.common.enums.AiFeature

data class CreditDeductedEvent(
    val userId: Long,
    val amount: Int,
    val feature: AiFeature,
    val remainingBalance: Int,
)
