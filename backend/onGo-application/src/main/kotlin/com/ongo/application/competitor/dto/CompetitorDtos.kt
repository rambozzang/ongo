package com.ongo.application.competitor.dto

import java.time.LocalDateTime

data class CompetitorResponse(
    val id: Long,
    val platform: String,
    val platformChannelId: String,
    val channelName: String,
    val channelUrl: String?,
    val subscriberCount: Long,
    val totalViews: Long,
    val videoCount: Int,
    val avgViews: Long,
    val profileImageUrl: String?,
    val lastSyncedAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
)

data class CompetitorListResponse(
    val competitors: List<CompetitorResponse>,
    val totalCount: Int,
)

data class CreateCompetitorRequest(
    val platform: String,
    val platformChannelId: String,
    val channelName: String,
    val channelUrl: String? = null,
    val subscriberCount: Long = 0,
    val totalViews: Long = 0,
    val videoCount: Int = 0,
    val avgViews: Long = 0,
    val profileImageUrl: String? = null,
)

data class UpdateCompetitorRequest(
    val channelName: String? = null,
    val channelUrl: String? = null,
    val subscriberCount: Long? = null,
    val totalViews: Long? = null,
    val videoCount: Int? = null,
    val avgViews: Long? = null,
    val profileImageUrl: String? = null,
)

data class ChannelLookupRequest(
    val platform: String,
    val query: String,
)

data class ChannelLookupResponse(
    val found: Boolean,
    val platformChannelId: String? = null,
    val channelName: String? = null,
    val channelUrl: String? = null,
    val subscriberCount: Long = 0,
    val totalViews: Long = 0,
    val videoCount: Int = 0,
    val profileImageUrl: String? = null,
    val platform: String? = null,
    val requiresManualInput: Boolean = false,
    val message: String? = null,
)
