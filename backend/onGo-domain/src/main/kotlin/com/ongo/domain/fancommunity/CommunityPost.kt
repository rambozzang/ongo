package com.ongo.domain.fancommunity

import java.time.LocalDateTime

data class CommunityPost(
    val id: Long = 0,
    val workspaceId: Long,
    val type: String = "DISCUSSION",
    val title: String,
    val content: String,
    val authorName: String,
    val authorAvatar: String? = null,
    val isCreator: Boolean = false,
    val likes: Int = 0,
    val commentsCount: Int = 0,
    val shares: Int = 0,
    val isPinned: Boolean = false,
    val tags: List<String> = emptyList(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
