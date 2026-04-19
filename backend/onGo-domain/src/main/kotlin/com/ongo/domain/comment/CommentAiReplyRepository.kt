package com.ongo.domain.comment

interface CommentAiReplyRepository {
    fun findByCommentId(commentId: Long): List<CommentAiReply>
    fun findByUserId(userId: Long): List<CommentAiReply>
    fun save(reply: CommentAiReply): CommentAiReply
    fun markAsUsed(id: Long)
}
