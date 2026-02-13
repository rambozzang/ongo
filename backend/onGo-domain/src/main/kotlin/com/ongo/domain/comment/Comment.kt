package com.ongo.domain.comment

import java.time.LocalDateTime

data class Comment(
    val id: Long? = null,
    val userId: Long,
    val videoId: Long? = null,
    val platform: String? = null,
    val platformCommentId: String? = null,
    val authorName: String,
    val authorAvatarUrl: String? = null,
    val content: String,
    val sentiment: String = "NEUTRAL",
    val isReplied: Boolean = false,
    val replyContent: String? = null,
    val repliedAt: LocalDateTime? = null,
    val publishedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
