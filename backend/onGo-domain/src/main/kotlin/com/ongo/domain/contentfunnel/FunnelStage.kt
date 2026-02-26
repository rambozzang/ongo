package com.ongo.domain.contentfunnel

import java.math.BigDecimal
import java.time.LocalDateTime

data class FunnelStage(
    val id: Long? = null,
    val userId: Long,
    val videoId: Long,
    val videoTitle: String,
    val stage: String,
    val count: Long = 0,
    val rate: BigDecimal = BigDecimal.ZERO,
    val dropOff: BigDecimal = BigDecimal.ZERO,
    val measuredAt: LocalDateTime? = null,
)
