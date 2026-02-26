package com.ongo.domain.contentabanalyzer

import java.math.BigDecimal

data class ContentVariant(
    val id: Long? = null,
    val testId: Long,
    val label: String,
    val videoId: Long,
    val videoTitle: String,
    val views: Long = 0,
    val likes: Long = 0,
    val comments: Long = 0,
    val ctr: BigDecimal = BigDecimal.ZERO,
    val avgWatchTime: Int = 0,
)
