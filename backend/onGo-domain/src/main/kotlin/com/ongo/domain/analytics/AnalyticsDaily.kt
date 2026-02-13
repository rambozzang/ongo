package com.ongo.domain.analytics

import java.time.LocalDate
import java.time.LocalDateTime

data class AnalyticsDaily(
    val id: Long? = null,
    val videoUploadId: Long,
    val date: LocalDate,
    val views: Int = 0,
    val likes: Int = 0,
    val commentsCount: Int = 0,
    val shares: Int = 0,
    val watchTimeSeconds: Long = 0,
    val subscriberGained: Int = 0,
    val revenueMicro: Long = 0,
    val createdAt: LocalDateTime? = null,
)
