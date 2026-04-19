package com.ongo.domain.comment

import java.time.LocalDateTime

data class CommentAiReply(
    val id: Long? = null,
    val commentId: Long,
    val userId: Long,
    val generatedReply: String,
    val tone: String,
    val isUsed: Boolean = false,
    val createdAt: LocalDateTime? = null,
)
