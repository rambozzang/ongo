package com.ongo.domain.abtestresult

import java.math.BigDecimal
import java.time.LocalDateTime

data class ABTestResult(
    val id: Long = 0,
    val workspaceId: Long,
    val testId: Long? = null,
    val testName: String,
    val status: String = "RUNNING",
    val startDate: LocalDateTime,
    val endDate: LocalDateTime? = null,
    val winner: String? = null,
    val confidence: BigDecimal = BigDecimal.ZERO,
    val metric: String = "CTR",
    val platform: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
