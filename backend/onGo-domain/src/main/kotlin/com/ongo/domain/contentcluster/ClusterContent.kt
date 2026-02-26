package com.ongo.domain.contentcluster

import java.math.BigDecimal
import java.time.LocalDateTime

data class ClusterContent(
    val id: Long = 0,
    val clusterId: Long,
    val title: String,
    val platform: String,
    val views: Long = 0,
    val likes: Int = 0,
    val engagement: BigDecimal = BigDecimal.ZERO,
    val publishedAt: LocalDateTime? = null,
    val similarity: BigDecimal = BigDecimal.ZERO,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
