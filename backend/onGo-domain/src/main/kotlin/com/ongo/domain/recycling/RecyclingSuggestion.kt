package com.ongo.domain.recycling

import java.time.LocalDateTime

data class RecyclingSuggestion(
    val id: Long? = null,
    val userId: Long,
    val videoId: Long,
    val suggestionType: String,
    val reason: String? = null,
    val suggestedPlatforms: List<String> = emptyList(),
    val priorityScore: Int = 50,
    val status: String = "PENDING",
    val createdAt: LocalDateTime? = null,
)
