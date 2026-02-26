package com.ongo.application.fancommunity.dto

data class CommunityPostResponse(
    val id: Long,
    val type: String,
    val title: String,
    val content: String,
    val authorName: String,
    val authorAvatar: String?,
    val isCreator: Boolean,
    val likes: Int,
    val comments: Int,
    val shares: Int,
    val isPinned: Boolean,
    val tags: List<String>,
    val attachments: List<PostAttachmentResponse>,
    val createdAt: String,
    val updatedAt: String,
)

data class PostAttachmentResponse(
    val id: Long,
    val type: String,
    val url: String,
    val name: String?,
    val size: Long,
)

data class PostCommentResponse(
    val id: Long,
    val postId: Long,
    val authorName: String,
    val authorAvatar: String?,
    val isCreator: Boolean,
    val content: String,
    val likes: Int,
    val createdAt: String,
)

data class CreatePostRequest(
    val type: String = "DISCUSSION",
    val title: String,
    val content: String,
    val tags: List<String> = emptyList(),
    val isPinned: Boolean = false,
)

data class CreateCommentRequest(
    val content: String,
)

data class CommunitySummaryResponse(
    val totalPosts: Int,
    val totalMembers: Int,
    val activeMembersToday: Int,
    val totalLikes: Long,
    val postsThisWeek: Int,
)
