package com.ongo.domain.fancommunity

import java.time.LocalDateTime

data class PostComment(
    val id: Long = 0,
    val postId: Long,
    val authorName: String,
    val authorAvatar: String? = null,
    val isCreator: Boolean = false,
    val content: String,
    val likes: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
