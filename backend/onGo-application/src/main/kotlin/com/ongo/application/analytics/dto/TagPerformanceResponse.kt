package com.ongo.application.analytics.dto

data class TagPerformanceItem(
    val tag: String,
    val videoCount: Int,
    val totalViews: Long,
    val totalLikes: Long,
    val avgViews: Long,
    val avgEngagement: Double,
    val trend: String,  // "up" | "down" | "stable"
)

data class TagPerformanceResponse(
    val tags: List<TagPerformanceItem>,
)
