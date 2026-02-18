package com.ongo.application.comment

import com.ongo.application.comment.dto.CommentCapabilitiesDto
import com.ongo.application.comment.dto.CommentListResponse
import com.ongo.application.comment.dto.CommentResponse
import com.ongo.application.comment.dto.CommentStats
import com.ongo.domain.comment.Comment
import com.ongo.domain.comment.CommentRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class CommentUseCase(
    private val commentRepository: CommentRepository,
    private val commentEngagementUseCase: CommentEngagementUseCase,
) {

    fun listComments(
        userId: Long,
        videoId: Long?,
        platform: String?,
        sentiment: String?,
        searchText: String?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
        page: Int,
        size: Int,
    ): CommentListResponse {
        val comments = commentRepository.findByUserIdFiltered(
            userId = userId,
            videoId = videoId,
            platform = platform,
            sentiment = sentiment?.uppercase(),
            searchText = searchText,
            startDate = startDate,
            endDate = endDate,
            page = page,
            size = size,
        )

        val totalCount = commentRepository.countByUserIdFiltered(
            userId = userId,
            videoId = videoId,
            platform = platform,
            sentiment = sentiment?.uppercase(),
            searchText = searchText,
            startDate = startDate,
            endDate = endDate,
        )

        val stats = getCommentStats(userId)
        val capabilities = commentEngagementUseCase.getCapabilitiesMap(userId)

        return CommentListResponse(
            comments = comments.map { it.toResponse() },
            totalCount = totalCount,
            stats = stats,
            capabilities = capabilities,
        )
    }

    fun getCommentStats(userId: Long): CommentStats {
        val grouped = commentRepository.countByUserIdGroupedBySentiment(userId)
        val positive = grouped["POSITIVE"] ?: 0
        val neutral = grouped["NEUTRAL"] ?: 0
        val negative = grouped["NEGATIVE"] ?: 0
        return CommentStats(
            total = positive + neutral + negative,
            positive = positive,
            neutral = neutral,
            negative = negative,
        )
    }

    private fun Comment.toResponse(): CommentResponse = CommentResponse(
        id = id!!,
        videoId = videoId,
        platform = platform,
        platformCommentId = platformCommentId,
        authorName = authorName,
        authorAvatarUrl = authorAvatarUrl,
        authorChannelUrl = authorChannelUrl,
        content = content,
        sentiment = sentiment,
        likeCount = likeCount,
        replyCount = replyCount,
        isReplied = isReplied,
        isHidden = isHidden,
        isPinned = isPinned,
        replyContent = replyContent,
        repliedAt = repliedAt,
        publishedAt = publishedAt,
        syncedAt = syncedAt,
        createdAt = createdAt,
    )
}
