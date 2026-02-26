package com.ongo.domain.creatorbenchmark

import java.math.BigDecimal
import java.time.LocalDateTime

data class BenchmarkResult(
    val id: Long = 0,
    val workspaceId: Long,
    val platform: String,
    val category: String,
    val myValue: BigDecimal = BigDecimal.ZERO,
    val avgValue: BigDecimal = BigDecimal.ZERO,
    val topValue: BigDecimal = BigDecimal.ZERO,
    val percentile: Int = 0,
    val metric: String,
    val trend: String = "STABLE",
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
