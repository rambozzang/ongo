package com.ongo.domain.contentcluster

import java.math.BigDecimal
import java.time.LocalDateTime

data class ContentCluster(
    val id: Long = 0,
    val workspaceId: Long,
    val name: String,
    val description: String? = null,
    val contentCount: Int = 0,
    val avgViews: Long = 0,
    val avgEngagement: BigDecimal = BigDecimal.ZERO,
    val topContent: String? = null,
    val tags: List<String> = emptyList(),
    val platform: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
