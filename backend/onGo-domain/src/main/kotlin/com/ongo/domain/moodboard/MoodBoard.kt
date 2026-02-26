package com.ongo.domain.moodboard

import java.time.LocalDateTime

data class MoodBoard(
    val id: Long = 0,
    val workspaceId: Long,
    val name: String,
    val description: String? = null,
    val category: String? = null,
    val itemCount: Int = 0,
    val coverImage: String? = null,
    val tags: List<String> = emptyList(),
    val isPublic: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
