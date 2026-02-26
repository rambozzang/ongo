package com.ongo.domain.contentabanalyzer

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class ContentAbTest(
    val id: Long? = null,
    val userId: Long,
    val title: String,
    val status: String = "RUNNING",
    val winner: String? = null,
    val confidence: BigDecimal = BigDecimal.ZERO,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val createdAt: LocalDateTime? = null,
)
