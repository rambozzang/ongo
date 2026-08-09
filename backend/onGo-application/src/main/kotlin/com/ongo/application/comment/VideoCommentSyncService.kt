package com.ongo.application.comment

import com.ongo.application.ai.AnalyzeSentimentUseCase
import com.ongo.application.notification.WebSocketNotificationService
import com.ongo.common.enums.NotificationType
import com.ongo.common.enums.Platform
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.comment.Comment
import com.ongo.domain.comment.CommentRepository
import com.ongo.domain.comment.PlatformCommentPort
import com.ongo.domain.notification.Notification
import com.ongo.domain.notification.NotificationRepository
import com.ongo.domain.settings.UserSettingsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class VideoCommentSyncService(
    private val commentRepository: CommentRepository,
    private val platformCommentPort: PlatformCommentPort,
    private val channelRepository: ChannelRepository,
    private val tokenEncryptionPort: TokenEncryptionPort,
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
    fun syncVideoComments(
        userId: Long,
        videoId: Long,
        platform: Platform,
        platformVideoId: String,
        accessToken: PlainToken? = null,
    ): Pair<Int, Int> {
        val token = accessToken ?: run {
            val channel = channelRepository.findByUserIdAndPlatform(userId, platform)
                ?: throw IllegalStateException("채널을 찾을 수 없습니다: $platform")
            tokenEncryptionPort.decrypt(channel.accessToken)
        }

        // Incremental sync: 마지막 동기화 시간 이후의 댓글만 조회
        val latestSyncedAt = commentRepository.findLatestSyncedAtByVideoIdAndPlatform(videoId, platform.name)
        val publishedAfter = latestSyncedAt?.minusMinutes(1) // 1분 버퍼 (API 지연 고려)

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
                publishedAfter = publishedAfter,
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

        // 기존 댓글 여부 확인 (배치 조회)
        val existingIds = mutableSetOf<String>()
        val newComments = mutableListOf<Comment>()
        val platformCommentIds = allComments.mapNotNull { it.platformCommentId }.distinct()
        val existingComments = if (platformCommentIds.isNotEmpty()) {
            commentRepository.findByPlatformAndPlatformCommentIdsIn(platform.name, platformCommentIds)
        } else {
            emptyList()
        }
        val existingIdSet = existingComments.mapNotNull { it.platformCommentId }.toSet()

        for (comment in allComments) {
            val pcId = comment.platformCommentId
            if (pcId != null && existingIdSet.contains(pcId)) {
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

        // 삭제 동기화: 모든 댓글을 가져왔을 때만 수행 (MAX_PAGES 제한으로 인한 오탐 방지)
        if (pageToken == null && allComments.isNotEmpty()) {
            val fetchedIds = allComments.mapNotNull { it.platformCommentId }.toSet()
            val dbComments = commentRepository.findByVideoIdAndPlatform(videoId, platform.name)
            val dbIds = dbComments.mapNotNull { it.platformCommentId }.toSet()
            val deletedIds = (dbIds - fetchedIds).toList()
            if (deletedIds.isNotEmpty()) {
                val deletedCount = commentRepository.softDeleteByPlatformCommentIds(platform.name, deletedIds)
                log.info("삭제된 댓글 동기화: platform={}, videoId={}, deleted={}", platform, videoId, deletedCount)
            }
        }

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
