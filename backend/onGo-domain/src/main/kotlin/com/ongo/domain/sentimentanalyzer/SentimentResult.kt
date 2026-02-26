package com.ongo.domain.sentimentanalyzer

import java.math.BigDecimal
import java.time.LocalDateTime

data class SentimentResult(
    val id: Long = 0,
    val workspaceId: Long,
    val contentTitle: String,
    val platform: String,
    val totalComments: Int = 0,
    val positiveCount: Int = 0,
    val neutralCount: Int = 0,
    val negativeCount: Int = 0,
    val positiveRate: BigDecimal = BigDecimal.ZERO,
    val avgSentimentScore: Int = 0,
    val topPositiveKeywords: List<String> = emptyList(),
    val topNegativeKeywords: List<String> = emptyList(),
    val analyzedAt: LocalDateTime = LocalDateTime.now(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
