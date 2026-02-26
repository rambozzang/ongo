package com.ongo.domain.commentsummary

import java.time.LocalDateTime

data class TopComment(
    val id: Long = 0,
    val summaryId: Long,
    val author: String,
    val text: String,
    val likes: Int = 0,
    val sentiment: String = "NEUTRAL",
    val isHighlighted: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
