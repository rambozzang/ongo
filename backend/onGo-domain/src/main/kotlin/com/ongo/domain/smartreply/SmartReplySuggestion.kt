package com.ongo.domain.smartreply

import java.time.LocalDateTime

data class SmartReplySuggestion(
    val id: String,
    val userId: Long,
    val originalText: String,
    val originalAuthor: String? = null,
    val platform: String,
    val context: String = "COMMENT",
    val sentiment: String = "NEUTRAL",
    val suggestions: String = "[]",
    val videoId: String? = null,
    val videoTitle: String? = null,
    val createdAt: LocalDateTime? = null,
)
