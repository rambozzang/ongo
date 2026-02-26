package com.ongo.domain.audienceoverlap

import java.math.BigDecimal
import java.time.LocalDateTime

data class OverlapSegment(
    val id: Long = 0,
    val workspaceId: Long,
    val name: String,
    val platforms: List<String> = emptyList(),
    val audienceSize: Long = 0,
    val engagementRate: BigDecimal = BigDecimal.ZERO,
    val topInterest: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
