package com.ongo.application.comment

import com.ongo.application.comment.dto.CommentCapabilitiesDto
import com.ongo.application.comment.dto.CommentResponse
import com.ongo.common.enums.Platform
import com.ongo.common.enums.PlanType
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.exception.PlanLimitExceededException
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.comment.CommentRepository
import com.ongo.domain.comment.PlatformCommentPort
import com.ongo.domain.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class CommentEngagementUseCase(
    private val commentRepository: CommentRepository,
    private val platformCommentPort: PlatformCommentPort,
    private val channelRepository: ChannelRepository,
    private val tokenEncryptionPort: TokenEncryptionPort,
    private val userRepository: UserRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun replyToComment(userId: Long, commentId: Long, content: String): CommentResponse {
        validateCommentAccess(userId)
        val comment = commentRepository.findById(commentId)
            ?: throw NotFoundException("댓글", commentId)
        if (comment.userId != userId) throw ForbiddenException("해당 댓글에 대한 권한이 없습니다")

        var platformReplyId: String? = null

        // Send reply to platform if possible
        val commentPlatform = comment.platform
        val commentPlatformCommentId = comment.platformCommentId
        if (commentPlatform != null && commentPlatformCommentId != null) {
            try {
                val platform = Platform.valueOf(commentPlatform)
                val capabilities = platformCommentPort.getCommentCapabilities(platform)

                if (capabilities.canReply) {
                    val channel = channelRepository.findByUserIdAndPlatform(userId, platform)
                    if (channel != null) {
                        val accessToken = tokenEncryptionPort.decrypt(channel.accessToken)
                        val result = platformCommentPort.postReply(
                            platform = platform,
                            platformCommentId = commentPlatformCommentId,
                            content = content,
                            accessToken = accessToken,
                            platformVideoId = comment.platformVideoId,
                        )
                        if (result.success) {
                            platformReplyId = result.platformCommentId
                            log.info("플랫폼 답글 전송 성공: platform={}, replyId={}", platform, platformReplyId)
                        } else {
                            log.warn("플랫폼 답글 전송 실패: platform={}, error={}", platform, result.errorMessage)
                        }
                    }
                }
            } catch (e: Exception) {
                log.warn("플랫폼 답글 전송 중 오류: {}", e.message)
            }
        }

        val updated = comment.copy(
            isReplied = true,
            replyContent = content,
            repliedAt = LocalDateTime.now(),
            platformReplyId = platformReplyId,
        )
        return commentRepository.update(updated).toResponse()
    }

    @Transactional
    fun deleteComment(userId: Long, commentId: Long) {
        validateCommentAccess(userId)
        val comment = commentRepository.findById(commentId)
            ?: throw NotFoundException("댓글", commentId)
        if (comment.userId != userId) throw ForbiddenException("해당 댓글에 대한 권한이 없습니다")

        // Delete from platform if possible
        val delPlatform = comment.platform
        val delPlatformCommentId = comment.platformCommentId
        if (delPlatform != null && delPlatformCommentId != null) {
            try {
                val platform = Platform.valueOf(delPlatform)
                val capabilities = platformCommentPort.getCommentCapabilities(platform)

                if (capabilities.canDelete || capabilities.canHide) {
                    val channel = channelRepository.findByUserIdAndPlatform(userId, platform)
                    if (channel != null) {
                        val accessToken = tokenEncryptionPort.decrypt(channel.accessToken)
                        val result = platformCommentPort.deleteComment(
                            platform = platform,
                            platformCommentId = delPlatformCommentId,
                            accessToken = accessToken,
                        )
                        if (!result.success) {
                            log.warn("플랫폼 댓글 삭제 실패: platform={}, error={}", platform, result.errorMessage)
                        }
                    }
                }
            } catch (e: Exception) {
                log.warn("플랫폼 댓글 삭제 중 오류: {}", e.message)
            }
        }

        commentRepository.delete(commentId)
    }

    @Transactional
    fun hideComment(userId: Long, commentId: Long): CommentResponse {
        validateCommentAccess(userId)
        val comment = commentRepository.findById(commentId)
            ?: throw NotFoundException("댓글", commentId)
        if (comment.userId != userId) throw ForbiddenException("해당 댓글에 대한 권한이 없습니다")

        val updated = comment.copy(isHidden = !comment.isHidden)
        return commentRepository.update(updated).toResponse()
    }

    @Transactional
    fun pinComment(userId: Long, commentId: Long): CommentResponse {
        validateCommentAccess(userId)
        val comment = commentRepository.findById(commentId)
            ?: throw NotFoundException("댓글", commentId)
        if (comment.userId != userId) throw ForbiddenException("해당 댓글에 대한 권한이 없습니다")

        val updated = comment.copy(isPinned = !comment.isPinned)
        return commentRepository.update(updated).toResponse()
    }

    fun getCapabilities(platform: Platform): CommentCapabilitiesDto {
        val caps = platformCommentPort.getCommentCapabilities(platform)
        return CommentCapabilitiesDto(
            canListComments = caps.canListComments,
            canReply = caps.canReply,
            canLike = caps.canLike,
            canDelete = caps.canDelete,
            canHide = true,
        )
    }

    fun getCapabilitiesMap(userId: Long): Map<String, CommentCapabilitiesDto> {
        val channels = channelRepository.findByUserId(userId)
            .filter { it.status == "ACTIVE" }

        return channels.associate { channel ->
            channel.platform.name to getCapabilities(channel.platform)
        }
    }

    private fun validateCommentAccess(userId: Long) {
        val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
        if (user.planType != PlanType.PRO && user.planType != PlanType.BUSINESS) {
            throw PlanLimitExceededException("댓글 관리", 0)
        }
    }

    private fun com.ongo.domain.comment.Comment.toResponse(): CommentResponse = CommentResponse(
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
