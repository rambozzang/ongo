package com.ongo.application.comment

import com.ongo.application.comment.dto.*
import com.ongo.common.enums.PlanType
import com.ongo.common.exception.NotFoundException
import com.ongo.common.exception.PlanLimitExceededException
import com.ongo.domain.comment.Comment
import com.ongo.domain.comment.CommentRepository
import com.ongo.domain.user.UserRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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

    fun getSentimentTrend(userId: Long, days: Int): SentimentTrendResponse {
        validateCommentAccess(userId)
        val grouped = commentRepository.findSentimentGroupedByDate(userId, days)

        val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        val startDate = LocalDate.now().minusDays(days.toLong())

        val data = (0 until days).map { offset ->
            val date = startDate.plusDays(offset.toLong())
            val sentiments = grouped[date] ?: emptyMap()
            SentimentTrendPoint(
                date = date.format(dateFormatter),
                positive = sentiments["POSITIVE"] ?: 0,
                neutral = sentiments["NEUTRAL"] ?: 0,
                negative = sentiments["NEGATIVE"] ?: 0,
            )
        }

        val totalPositive = data.sumOf { it.positive }
        val totalNeutral = data.sumOf { it.neutral }
        val totalNegative = data.sumOf { it.negative }

        val firstHalf = data.take(days / 2)
        val secondHalf = data.drop(days / 2)
        val firstNeg = firstHalf.sumOf { it.negative }
        val secondNeg = secondHalf.sumOf { it.negative }
        val trend = when {
            secondNeg > firstNeg * 1.2 -> "WORSENING"
            secondNeg < firstNeg * 0.8 -> "IMPROVING"
            else -> "STABLE"
        }

        return SentimentTrendResponse(
            data = data,
            summary = SentimentSummary(totalPositive, totalNeutral, totalNegative, trend),
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
