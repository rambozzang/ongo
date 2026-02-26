package com.ongo.application.sociallistening.dto

import java.time.LocalDateTime

data class ListeningReportResponse(
    val period: String,
    val totalMentions: Int,
    val sentimentBreakdown: SentimentBreakdown,
    val mentions: List<BrandMentionResponse>,
)

data class SentimentBreakdown(
    val positive: Int = 0,
    val neutral: Int = 0,
    val negative: Int = 0,
)

data class BrandMentionResponse(
    val id: Long,
    val platform: String,
    val mentionText: String?,
    val authorName: String?,
    val authorUrl: String?,
    val sentiment: String,
    val reach: Long,
    val sourceUrl: String?,
    val mentionedAt: LocalDateTime?,
)

data class CreateKeywordAlertRequest(
    val keyword: String,
    val platforms: List<String> = emptyList(),
    val notifyEmail: Boolean = false,
    val notifyPush: Boolean = true,
)

data class KeywordAlertResponse(
    val id: Long,
    val keyword: String,
    val platforms: String,
    val enabled: Boolean,
    val notifyEmail: Boolean,
    val notifyPush: Boolean,
    val lastTriggeredAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
)
