package com.ongo.application.comment

import com.ongo.application.comment.dto.CommentCapabilitiesDto
import com.ongo.application.comment.dto.CommentListResponse
import com.ongo.application.comment.dto.CommentResponse
import com.ongo.application.comment.dto.CommentStats
import com.ongo.common.enums.PlanType
import com.ongo.common.exception.NotFoundException
import com.ongo.common.exception.PlanLimitExceededException
import com.ongo.domain.comment.Comment
import com.ongo.domain.comment.CommentRepository
import com.ongo.domain.user.UserRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class CommentUseCase(
    private val commentRepository: CommentRepository,
    private val commentEngagementUseCase: CommentEngagementUseCase,
    private val userRepository: UserRepository,
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
        validateCommentAccess(userId)
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

    private fun validateCommentAccess(userId: Long) {
        val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
        if (user.planType != PlanType.PRO && user.planType != PlanType.BUSINESS) {
            throw PlanLimitExceededException("댓글 관리", 0)
        }
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
