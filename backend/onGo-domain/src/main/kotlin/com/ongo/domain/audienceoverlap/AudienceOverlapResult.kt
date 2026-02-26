package com.ongo.domain.audienceoverlap

import java.math.BigDecimal
import java.time.LocalDateTime

data class AudienceOverlapResult(
    val id: Long = 0,
    val workspaceId: Long,
    val platformA: String,
    val platformB: String,
    val overlapPercent: BigDecimal = BigDecimal.ZERO,
    val uniqueToA: Long = 0,
    val uniqueToB: Long = 0,
    val sharedAudience: Long = 0,
    val analyzedAt: LocalDateTime = LocalDateTime.now(),
)
