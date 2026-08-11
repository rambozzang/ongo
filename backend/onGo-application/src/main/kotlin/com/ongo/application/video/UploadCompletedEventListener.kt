package com.ongo.application.video

import com.ongo.application.notification.WebSocketNotificationService
import com.ongo.common.enums.NotificationType
import com.ongo.domain.notification.Notification
import com.ongo.domain.notification.NotificationRepository
import com.ongo.domain.ugc.publishing.CampaignPostRepository
import com.ongo.domain.ugc.publishing.PostStatus
import com.ongo.domain.ugc.publishing.PostType
import com.ongo.domain.ugc.shorts.ClipPublicationRepository
import com.ongo.domain.ugc.shorts.ClipPublicationStatus
import com.ongo.domain.ugc.submission.SubmissionRepository
import com.ongo.domain.ugc.submission.SubmissionStatus
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class UploadCompletedEventListener(
    private val notificationRepository: NotificationRepository,
    private val webSocketNotificationService: WebSocketNotificationService,
    private val campaignPostRepository: CampaignPostRepository,
    private val submissionRepository: SubmissionRepository,
    private val clipPublicationRepository: ClipPublicationRepository,
) {

    private val log = LoggerFactory.getLogger(UploadCompletedEventListener::class.java)

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun handleUploadCompleted(event: UploadCompletedEvent) {
        reconcileCampaignPosts(event)
        reconcileShortsPublications(event)

        val (type, title, message) = if (event.success) {
            Triple(
                NotificationType.UPLOAD_COMPLETE,
                "${event.platform.name} 업로드 완료",
                "영상이 ${event.platform.name}에 성공적으로 업로드되었습니다."
            )
        } else {
            Triple(
                NotificationType.UPLOAD_FAILED,
                "${event.platform.name} 업로드 실패",
                event.errorMessage ?: "업로드 중 오류가 발생했습니다."
            )
        }

        val notification = Notification(
            userId = event.userId,
            type = type,
            title = title,
            message = message,
            referenceType = "video",
            referenceId = event.videoId,
        )
        notificationRepository.save(notification)
        webSocketNotificationService.sendToUser(event.userId, type.name, mapOf("videoId" to event.videoId))

        log.info("업로드 완료 알림 전송. userId: {}, platform: {}, success: {}", event.userId, event.platform, event.success)
    }

    private fun reconcileCampaignPosts(event: UploadCompletedEvent) {
        val uploadId = event.videoUploadId ?: return
        val posts = campaignPostRepository.findByVideoUploadId(uploadId)
            // videoUploadId identifies the exact channel row, including multi-account
            // targets such as YOUTUBE#123. Filtering by the enum name would silently
            // miss those campaign posts.
            .filter { it.postType == PostType.DIRECT }
        if (posts.isEmpty()) return

        val nextStatus = if (event.success) PostStatus.PUBLISHED else PostStatus.FAILED
        posts.forEach { post ->
            campaignPostRepository.updateStatus(
                id = post.id!!,
                status = nextStatus,
                platformPostId = event.platformPostId ?: post.platformPostId,
                errorMessage = if (event.success) null else event.errorMessage,
            )
        }

        val submission = submissionRepository.findById(posts.first().submissionId) ?: return
        if (submission.status != SubmissionStatus.PUBLISHING) return
        val allPosts = campaignPostRepository.findBySubmissionId(submission.id!!)
            .filter { it.postType == PostType.DIRECT }
        val hasFailed = allPosts.any { it.status == PostStatus.FAILED }
        val allPublished = allPosts.isNotEmpty() && allPosts.all { it.status == PostStatus.PUBLISHED }
        when {
            hasFailed -> submissionRepository.updateStatus(submission.markPublishFailed())
            allPublished -> submissionRepository.updateStatus(submission.markPublished())
        }
    }

    private fun reconcileShortsPublications(event: UploadCompletedEvent) {
        val uploadId = event.videoUploadId ?: return
        clipPublicationRepository.findByVideoUploadId(uploadId)
            .forEach { publication ->
                clipPublicationRepository.update(
                    publication.copy(
                        status = if (event.success) ClipPublicationStatus.PUBLISHED else ClipPublicationStatus.FAILED,
                        publishedAt = if (event.success) Instant.now() else publication.publishedAt,
                        errorMessage = if (event.success) null else event.errorMessage,
                    ),
                )
            }
    }
}
