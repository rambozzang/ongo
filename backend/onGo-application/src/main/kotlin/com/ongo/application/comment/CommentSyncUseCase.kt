package com.ongo.application.comment

import com.ongo.application.ai.AnalyzeSentimentUseCase
import com.ongo.application.comment.dto.CommentSyncResult
import com.ongo.application.notification.WebSocketNotificationService
import com.ongo.common.enums.NotificationType
import com.ongo.common.enums.Platform
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.UploadStatus
import com.ongo.common.exception.NotFoundException
import com.ongo.common.exception.PlanLimitExceededException
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.comment.Comment
import com.ongo.domain.comment.CommentRepository
import com.ongo.domain.comment.PlatformCommentPort
import com.ongo.domain.notification.Notification
import com.ongo.domain.notification.NotificationRepository
import com.ongo.domain.settings.UserSettingsRepository
import com.ongo.domain.user.UserRepository
import com.ongo.domain.video.VideoUploadRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class CommentSyncUseCase(
    private val commentRepository: CommentRepository,
    private val platformCommentPort: PlatformCommentPort,
    private val channelRepository: ChannelRepository,
    private val videoUploadRepository: VideoUploadRepository,
    private val tokenEncryptionPort: TokenEncryptionPort,
    private val userRepository: UserRepository,
    private val analyzeSentimentUseCase: AnalyzeSentimentUseCase,
    private val notificationRepository: NotificationRepository,
    private val webSocketNotificationService: WebSocketNotificationService,
    private val settingsRepository: UserSettingsRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAX_PAGES = 10
        private const val PAGE_SIZE = 100
    }

    @Transactional
    fun syncAllComments(userId: Long): CommentSyncResult {
        val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
        if (user.planType != PlanType.PRO && user.planType != PlanType.BUSINESS) {
            throw PlanLimitExceededException("댓글 관리", 0)
        }
        log.info("전체 댓글 동기화 시작: userId={}", userId)

        val channels = channelRepository.findByUserId(userId)
            .filter { it.status == "ACTIVE" }

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
                tokenEncryptionPort.decrypt(channel.accessToken)
            } catch (e: Exception) {
                errors.add("${platform.name}: 토큰 복호화 실패")
                continue
            }

            val platformUploads = uploads.filter { it.platform == platform }

            for (upload in platformUploads) {
                try {
                    val result = syncVideoComments(
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

    @Transactional
    fun syncVideoComments(
        userId: Long,
        videoId: Long,
        platform: Platform,
        platformVideoId: String,
        accessToken: String? = null,
    ): Pair<Int, Int> {
        val token = accessToken ?: run {
            val channel = channelRepository.findByUserIdAndPlatform(userId, platform)
                ?: throw IllegalStateException("채널을 찾을 수 없습니다: $platform")
            tokenEncryptionPort.decrypt(channel.accessToken)
        }

        val allComments = mutableListOf<Comment>()
        var pageToken: String? = null
        var pages = 0

        do {
            val result = platformCommentPort.fetchComments(
                platform = platform,
                platformVideoId = platformVideoId,
                accessToken = token,
                pageToken = pageToken,
                maxResults = PAGE_SIZE,
            )

            val comments = result.comments.map { fetched ->
                Comment(
                    userId = userId,
                    videoId = videoId,
                    platform = platform.name,
                    platformCommentId = fetched.platformCommentId,
                    platformVideoId = platformVideoId,
                    authorName = fetched.authorName,
                    authorAvatarUrl = fetched.authorAvatarUrl,
                    authorChannelUrl = fetched.authorChannelUrl,
                    content = fetched.content,
                    likeCount = fetched.likeCount,
                    replyCount = fetched.replyCount,
                    publishedAt = fetched.publishedAt,
                    syncedAt = LocalDateTime.now(),
                )
            }

            allComments.addAll(comments)
            pageToken = result.nextPageToken
            pages++
        } while (pageToken != null && pages < MAX_PAGES)

        // 기존 댓글 여부 확인
        val existingIds = mutableSetOf<String>()
        val newComments = mutableListOf<Comment>()
        for (comment in allComments) {
            val p = comment.platform
            val pcId = comment.platformCommentId
            if (p != null && pcId != null &&
                commentRepository.findByPlatformAndPlatformCommentId(p, pcId) != null
            ) {
                existingIds.add(pcId)
            } else if (pcId != null) {
                newComments.add(comment)
            }
        }

        // AI 감정분석 (신규 댓글만)
        val enrichedComments = if (newComments.isNotEmpty()) {
            val sentiments = try {
                analyzeSentimentUseCase.analyzeBatch(userId, newComments.map { it.content })
            } catch (e: Exception) {
                log.warn("감정분석 스킵: {}", e.message)
                newComments.map { "NEUTRAL" }
            }
            val sentimentByPcId = newComments.mapIndexed { i, c ->
                c.platformCommentId to sentiments.getOrElse(i) { "NEUTRAL" }
            }.toMap()

            allComments.map { comment ->
                val sentiment = sentimentByPcId[comment.platformCommentId]
                if (sentiment != null) comment.copy(sentiment = sentiment) else comment
            }
        } else {
            allComments
        }

        val upserted = commentRepository.upsertBatch(enrichedComments)
        val newCount = newComments.size

        // 신규 댓글이 있으면 알림 전송 (realtime 설정 시)
        if (newCount > 0) {
            try {
                val settings = settingsRepository.findByUserId(userId)
                if (settings?.notificationComment == "realtime") {
                    val notification = Notification(
                        userId = userId,
                        type = NotificationType.COMMENT,
                        title = "새 댓글 ${newCount}개",
                        message = "${platform.name}에서 새 댓글 ${newCount}개가 도착했습니다.",
                    )
                    notificationRepository.save(notification)
                    webSocketNotificationService.sendToUser(
                        userId = userId,
                        type = "COMMENT",
                        payload = mapOf("newCount" to newCount, "platform" to platform.name, "videoId" to videoId),
                    )
                }
            } catch (e: Exception) {
                log.warn("댓글 알림 전송 실패: {}", e.message)
            }
        }

        log.debug("영상 댓글 동기화 완료: platform={}, videoId={}, total={}, new={}",
            platform, platformVideoId, upserted, newCount)

        return Pair(upserted, newCount)
    }
}
