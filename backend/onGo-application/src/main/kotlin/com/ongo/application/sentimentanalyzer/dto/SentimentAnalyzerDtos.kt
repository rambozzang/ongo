package com.ongo.application.sentimentanalyzer.dto

data class SentimentResultResponse(
    val id: Long,
    val contentTitle: String,
    val platform: String,
    val totalComments: Int,
    val positiveCount: Int,
    val neutralCount: Int,
    val negativeCount: Int,
    val positiveRate: Double,
    val avgSentimentScore: Int,
    val topPositiveKeywords: List<String>,
    val topNegativeKeywords: List<String>,
    val analyzedAt: String,
    val createdAt: String,
)

data class CommentSentimentResponse(
    val id: Long,
    val commentText: String,
    val authorName: String,
    val sentiment: String,
    val score: Int,
    val keywords: List<String>,
    val createdAt: String,
)

data class AnalyzeSentimentRequest(
    val contentId: Long,
    val contentTitle: String,
    val platform: String,
)

data class SentimentAnalyzerSummaryResponse(
    val totalAnalyzed: Int,
    val avgPositiveRate: Double,
    val avgSentimentScore: Double,
    val mostPositiveContent: String,
    val mostNegativeContent: String,
)

data class SentimentTrendPoint(
    val date: String,
    val positiveRate: Double,
    val neutralRate: Double,
    val negativeRate: Double,
    val avgScore: Double,
    val totalComments: Int,
)
