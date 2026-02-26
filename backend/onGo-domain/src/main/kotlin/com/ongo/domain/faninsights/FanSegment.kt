package com.ongo.domain.faninsights

import java.math.BigDecimal
import java.time.LocalDateTime

data class FanSegment(
    val id: Long = 0,
    val workspaceId: Long,
    val name: String,
    val description: String? = null,
    val memberCount: Int = 0,
    val avgEngagement: BigDecimal = BigDecimal.ZERO,
    val topInterests: List<String> = emptyList(),
    val platform: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
