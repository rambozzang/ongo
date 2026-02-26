package com.ongo.domain.commentsummary

import java.time.LocalDateTime

data class CommentSummaryResult(
    val id: Long = 0,
    val workspaceId: Long,
    val videoTitle: String,
    val platform: String,
    val totalComments: Int = 0,
    val positivePct: Int = 0,
    val negativePct: Int = 0,
    val neutralPct: Int = 0,
    val topTopics: List<String> = emptyList(),
    val aiSummary: String? = null,
    val analyzedAt: LocalDateTime = LocalDateTime.now(),
)
