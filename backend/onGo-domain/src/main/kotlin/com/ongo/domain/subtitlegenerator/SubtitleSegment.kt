package com.ongo.domain.subtitlegenerator

import java.math.BigDecimal

data class SubtitleSegment(
    val id: Long = 0,
    val jobId: Long,
    val startTime: BigDecimal,
    val endTime: BigDecimal,
    val text: String,
    val confidence: BigDecimal = BigDecimal.ZERO,
)
