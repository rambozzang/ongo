package com.ongo.domain.trendpredictor

import java.math.BigDecimal

data class TrendTopic(
    val id: Long? = null,
    val predictionId: Long,
    val topic: String,
    val relatedKeywords: List<String> = emptyList(),
    val videoCount: Int = 0,
    val avgViews: Long = 0,
    val growthRate: BigDecimal = BigDecimal.ZERO,
)
