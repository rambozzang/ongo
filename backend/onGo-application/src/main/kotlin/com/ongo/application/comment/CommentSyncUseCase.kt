package com.ongo.application.comment

import com.ongo.application.comment.dto.CommentSyncResult
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.UploadStatus
import com.ongo.common.exception.NotFoundException
import com.ongo.common.exception.PlanLimitExceededException
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.ChannelStatus
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.comment.PlatformCommentPort
import com.ongo.domain.user.UserRepository
import com.ongo.domain.video.VideoUploadRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CommentSyncUseCase(
    private val channelRepository: ChannelRepository,
    private val videoUploadRepository: VideoUploadRepository,
    private val tokenEncryptionPort: TokenEncryptionPort,
    private val userRepository: UserRepository,
    private val platformCommentPort: PlatformCommentPort,
    private val videoCommentSyncService: VideoCommentSyncService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun syncAllComments(userId: Long): CommentSyncResult {
        val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
        if (user.planType != PlanType.PRO && user.planType != PlanType.BUSINESS) {
            throw PlanLimitExceededException("댓글 관리", 0)
        }
        log.info("전체 댓글 동기화 시작: userId={}", userId)

        val channels = channelRepository.findByUserId(userId)
            .filter { it.status == ChannelStatus.ACTIVE }

        val uploads = videoUploadRepository.findByUserId(userId)
            .filter { it.status == UploadStatus.PUBLISHED && it.platformVideoId != null }

        var totalSynced = 0
        var totalNew = 0
        val errors = mutableListOf<String>()

        for (channel in channels) {
            val platform = channel.platform
            val capabilities = platformCommentPort.getCommentCapabilities(platform)
            if (!capabilities.canListComments) continue

            val accessToken = try {
                tokenEncryptionPort.decrypt(channel.accessToken).value
            } catch (e: Exception) {
                errors.add("${platform.name}: 토큰 복호화 실패")
                continue
            }

            val platformUploads = uploads.filter { it.platform == platform }

            for (upload in platformUploads) {
                try {
                    val result = videoCommentSyncService.syncVideoComments(
                        userId = userId,
                        videoId = upload.videoId,
                        platform = platform,
                        platformVideoId = upload.platformVideoId!!,
                        accessToken = accessToken,
                    )
                    totalSynced += result.first
                    totalNew += result.second
                } catch (e: Exception) {
                    val msg = "${platform.name}/${upload.platformVideoId}: ${e.message}"
                    log.warn("댓글 동기화 실패: {}", msg)
                    errors.add(msg)
                }
            }
        }

        log.info("전체 댓글 동기화 완료: synced={}, new={}, errors={}", totalSynced, totalNew, errors.size)
        return CommentSyncResult(totalSynced, totalNew, errors)
    }
}
