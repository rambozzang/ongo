package com.ongo.application.commentsummary.dto

data class CommentSummaryResultResponse(
    val id: Long,
    val videoTitle: String,
    val platform: String,
    val totalComments: Int,
    val positivePct: Int,
    val negativePct: Int,
    val neutralPct: Int,
    val topTopics: List<String>,
    val aiSummary: String?,
    val analyzedAt: String,
)

data class TopCommentResponse(
    val id: Long,
    val summaryId: Long,
    val author: String,
    val text: String,
    val likes: Int,
    val sentiment: String,
    val isHighlighted: Boolean,
)

data class CommentSummarySummaryResponse(
    val totalAnalyzed: Int,
    val avgPositive: Double,
    val avgNegative: Double,
    val mostDiscussedTopic: String,
    val totalComments: Int,
)
