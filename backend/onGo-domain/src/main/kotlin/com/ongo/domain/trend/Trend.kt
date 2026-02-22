package com.ongo.domain.trend

import java.time.LocalDate
import java.time.LocalDateTime

data class Trend(
    val id: Long? = null,
    val category: String? = null,
    val keyword: String,
    val score: Double = 0.0,
    val source: String,
    val platform: String? = null,
    val region: String = "KR",
    val date: LocalDate = LocalDate.now(),
    val metadata: String? = null,
    val createdAt: LocalDateTime? = null,
)
