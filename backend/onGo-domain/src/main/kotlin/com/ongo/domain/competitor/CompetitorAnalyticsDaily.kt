package com.ongo.domain.competitor

import java.time.LocalDate
import java.time.LocalDateTime

data class CompetitorAnalyticsDaily(
    val id: Long? = null,
    val competitorId: Long,
    val date: LocalDate,
    val subscriberCount: Long = 0,
    val videoCount: Int = 0,
    val avgViews: Long = 0,
    val avgLikes: Long = 0,
    val avgComments: Long = 0,
    val totalViews: Long = 0,
    val createdAt: LocalDateTime? = null,
)
