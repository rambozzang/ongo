package com.ongo.domain.fanpoll

import java.math.BigDecimal

data class PollOption(
    val id: Long = 0,
    val pollId: Long,
    val text: String,
    val votes: Int = 0,
    val percentage: BigDecimal = BigDecimal.ZERO,
)
