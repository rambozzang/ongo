package com.ongo.domain.sentimentanalyzer

import java.time.LocalDateTime

data class CommentSentiment(
    val id: Long = 0,
    val resultId: Long,
    val commentText: String,
    val authorName: String,
    val sentiment: String = "NEUTRAL",
    val score: Int = 50,
    val keywords: List<String> = emptyList(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
