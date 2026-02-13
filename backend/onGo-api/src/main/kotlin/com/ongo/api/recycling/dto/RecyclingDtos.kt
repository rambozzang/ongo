package com.ongo.api.recycling.dto

import java.time.LocalDateTime

data class RecyclingSuggestionResponse(
    val id: Long,
    val videoId: Long,
    val suggestionType: String,
    val reason: String?,
    val suggestedPlatforms: List<String>,
    val priorityScore: Int,
    val status: String,
    val createdAt: LocalDateTime?,
)
