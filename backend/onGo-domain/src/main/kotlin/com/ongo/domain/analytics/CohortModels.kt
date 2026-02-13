package com.ongo.domain.analytics

data class CohortGroup(
    val name: String,
    val videoCount: Int,
    val totalViews: Long,
    val cumulativeViews: Map<Int, Long>,
)

data class RetentionPoint(
    val timestamp: Int,
    val retentionRate: Double,
    val viewCount: Long,
)
