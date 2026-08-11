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
    val authorChannelUrl: String? = null,
    val content: String,
    val sentiment: String = "NEUTRAL",
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val parentCommentId: Long? = null,
    val platformVideoId: String? = null,
    val platformReplyId: String? = null,
    val platformLikeId: String? = null,
    val isReplied: Boolean = false,
    val isHidden: Boolean = false,
    val isPinned: Boolean = false,
    val replyContent: String? = null,
    val repliedAt: LocalDateTime? = null,
    val publishedAt: LocalDateTime? = null,
    val syncedAt: LocalDateTime? = null,
    val deletedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
) {
    companion object {
        /** AI 분석을 수행하지 못한 댓글을 실제 중립 감정과 구분한다. */
        const val SENTIMENT_UNANALYZED = "UNANALYZED"
    }
}
