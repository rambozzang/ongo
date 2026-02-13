package com.ongo.domain.analytics

data class DashboardKpi(
    val totalViews: Long,
    val totalViewsChange: Double,
    val totalSubscribers: Long,
    val totalSubscribersChange: Long,
    val totalLikes: Long,
    val totalLikesChange: Double,
    val creditBalance: Int,
    val creditTotal: Int,
)
