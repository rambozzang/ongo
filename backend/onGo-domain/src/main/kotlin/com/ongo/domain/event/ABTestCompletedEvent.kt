package com.ongo.domain.event

data class ABTestCompletedEvent(
    val testId: Long,
    val userId: Long,
    val winnerVariantId: Long,
)
