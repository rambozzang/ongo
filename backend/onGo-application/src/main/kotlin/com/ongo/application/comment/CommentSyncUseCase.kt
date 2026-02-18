package com.ongo.application.comment

import com.ongo.application.comment.dto.CommentSyncResult
import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.comment.Comment
import com.ongo.domain.comment.CommentRepository
import com.ongo.domain.comment.PlatformCommentPort
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
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAX_PAGES = 10
        private const val PAGE_SIZE = 100
    }

    @Transactional
    fun syncAllComments(userId: Long): CommentSyncResult {
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

        val existingCount = allComments.count { comment ->
            val p = comment.platform
            val pcId = comment.platformCommentId
            p != null && pcId != null &&
                commentRepository.findByPlatformAndPlatformCommentId(p, pcId) != null
        }

        val upserted = commentRepository.upsertBatch(allComments)
        val newCount = allComments.size - existingCount

        log.debug("영상 댓글 동기화 완료: platform={}, videoId={}, total={}, new={}",
            platform, platformVideoId, upserted, newCount)

        return Pair(upserted, newCount)
    }
}
