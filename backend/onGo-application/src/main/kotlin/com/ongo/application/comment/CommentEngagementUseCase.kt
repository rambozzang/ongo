package com.ongo.application.comment

import com.ongo.application.comment.dto.CommentCapabilitiesDto
import com.ongo.application.comment.dto.CommentResponse
import com.ongo.common.enums.Platform
import com.ongo.common.enums.PlanType
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.exception.PlanLimitExceededException
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.ChannelStatus
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

        /*
         * **외부 전송이 성공해야만 로컬을 답변 완료로 바꾼다.**
         *
         * 예전에는 세 갈래가 조용히 성공으로 끝났다 — 플랫폼 식별자가 없을 때, 그 플랫폼이
         * 답글을 지원하지 않을 때, 연결된 채널이 없을 때. 셋 다 외부에 아무것도 보내지
         * 않은 채 `isReplied = true` 를 저장하고 200 을 돌려줬다. 화면의 capability 게이트를
         * 지나지 않는 API 직접 호출에서는 "답글을 달았다" 는 기록만 남고 시청자는 아무
         * 답글도 받지 못한다. 그 기록을 근거로 다시 답글을 달 방법도 없어진다.
         */
        val commentPlatform = comment.platform
            ?: throw BusinessException(
                "COMMENT_PLATFORM_MISSING",
                "이 댓글에는 플랫폼 정보가 없어 답글을 보낼 수 없습니다.",
            )
        // 공백 문자열도 식별자가 아니다. 그대로 보내면 플랫폼이 엉뚱한 대상에 달거나
        // 400 을 돌려주는데, 어느 쪽이든 여기서 걸러야 로컬에 거짓 기록이 남지 않는다.
        val commentPlatformCommentId = comment.platformCommentId?.takeIf { it.isNotBlank() }
            ?: throw BusinessException(
                "COMMENT_PLATFORM_ID_MISSING",
                "이 댓글에는 플랫폼 댓글 식별자가 없어 답글을 보낼 수 없습니다.",
            )
        val platform = runCatching { Platform.valueOf(commentPlatform) }
            .getOrElse {
                throw BusinessException(
                    "COMMENT_PLATFORM_UNSUPPORTED",
                    "지원하지 않는 플랫폼의 댓글입니다: $commentPlatform",
                )
            }

        val capabilities = platformCommentPort.getCommentCapabilities(platform)
        if (!capabilities.canReply) {
            throw BusinessException(
                "COMMENT_REPLY_UNSUPPORTED",
                "${platform.name}은(는) 답글 작성을 지원하지 않습니다.",
            )
        }

        val channel = channelRepository.findByUserIdAndPlatform(userId, platform)
            ?: throw NotFoundException("채널", "${platform.name} (userId=$userId)")

        val accessToken = tokenEncryptionPort.decrypt(channel.accessToken)
        val result = platformCommentPort.postReply(
            platform = platform,
            platformCommentId = commentPlatformCommentId,
            content = content,
            accessToken = accessToken,
            platformVideoId = comment.platformVideoId,
        )
        if (!result.success) {
            throw com.ongo.common.exception.PlatformApiException(
                platform.name,
                "댓글 답변 실패: ${result.errorMessage}"
            )
        }
        /*
         * 성공이라면서 식별자가 비어 있는 응답은 신뢰하지 않는다. 그 값 없이 저장하면
         * 나중에 우리가 단 답글을 플랫폼에서 찾거나 수정할 방법이 없다.
         */
        val platformReplyId = result.platformCommentId.takeIf { it.isNotBlank() }
            ?: throw com.ongo.common.exception.PlatformApiException(
                platform.name,
                "답글은 전송됐지만 플랫폼이 답글 식별자를 주지 않았습니다. 플랫폼에서 직접 확인해 주세요.",
            )
        log.info("플랫폼 답글 전송 성공: platform={}, replyId={}", platform, platformReplyId)

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
            val platform = Platform.valueOf(delPlatform)
            val capabilities = platformCommentPort.getCommentCapabilities(platform)

            // canHide is not equivalent to canDelete. Some providers (for
            // example Threads) expose moderation/hide semantics but have no
            // delete endpoint. Calling the default delete operation in that
            // case turns a local comment deletion into a provider error.
            if (capabilities.canDelete) {
                val channel = channelRepository.findByUserIdAndPlatform(userId, platform)
                if (channel != null) {
                    val accessToken = tokenEncryptionPort.decrypt(channel.accessToken)
                    val result = platformCommentPort.deleteComment(
                        platform = platform,
                        platformCommentId = delPlatformCommentId,
                        accessToken = accessToken,
                    )
                    if (!result.success) {
                        throw com.ongo.common.exception.PlatformApiException(
                            platform.name,
                            "댓글 삭제 실패: ${result.errorMessage}"
                        )
                    }
                }
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
            .filter { it.status == ChannelStatus.ACTIVE }

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
