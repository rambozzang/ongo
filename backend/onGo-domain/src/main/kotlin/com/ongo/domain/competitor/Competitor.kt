package com.ongo.domain.competitor

import java.time.LocalDateTime

data class Competitor(
    val id: Long? = null,
    val userId: Long,
    val platform: String,
    val platformChannelId: String,
    val channelName: String,
    val channelUrl: String? = null,
    val subscriberCount: Long = 0,
    val totalViews: Long = 0,
    val videoCount: Int = 0,
    val avgViews: Long = 0,
    val profileImageUrl: String? = null,
    val lastSyncedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
