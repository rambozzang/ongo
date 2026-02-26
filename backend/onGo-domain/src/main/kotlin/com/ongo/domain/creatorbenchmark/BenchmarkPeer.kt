package com.ongo.domain.creatorbenchmark

import java.math.BigDecimal
import java.time.LocalDateTime

data class BenchmarkPeer(
    val id: Long = 0,
    val workspaceId: Long,
    val name: String,
    val platform: String,
    val subscribers: Long = 0,
    val avgViews: Long = 0,
    val engagementRate: BigDecimal = BigDecimal.ZERO,
    val category: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
