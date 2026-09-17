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

        /*
         * **결과가 셋이라 문구도 셋이다.**
         *
         * UNCONFIRMED 를 "업로드 실패" 로 알리면 사용자는 다시 올린다. 그런데 그 게시물은
         * 이미 올라가 있을 수 있다 — 플랫폼에 중복이 생기고, 되돌리는 것은 사용자 몫이다.
         *
         * 그렇다고 성공이라 할 수도 없다. 그래서 **모른다는 사실 자체를 그대로 전한다.**
         * 알림 타입은 UPLOAD_FAILED 를 쓴다. 주의를 끌어야 하는 것은 맞고, 새 타입을
         * 더하려면 PostgreSQL enum 까지 함께 넓혀야 하는데 그 값이 실어야 할 정보는
         * 이미 제목·본문에 다 들어간다.
         */
        val (type, title, message) = when (event.outcome) {
            UploadOutcome.PUBLISHED -> Triple(
                NotificationType.UPLOAD_COMPLETE,
                "${event.platform.name} 업로드 완료",
                "영상이 ${event.platform.name}에 성공적으로 업로드되었습니다."
            )
            UploadOutcome.FAILED -> Triple(
                NotificationType.UPLOAD_FAILED,
                "${event.platform.name} 업로드 실패",
                event.errorMessage ?: "업로드 중 오류가 발생했습니다."
            )
            UploadOutcome.UNCONFIRMED -> Triple(
                NotificationType.UPLOAD_FAILED,
                "${event.platform.name} 게시 결과 확인 필요",
                "${event.platform.name}에서 게시 결과를 확인하지 못했습니다. " +
                    "이미 게시됐을 수 있으니 **채널을 먼저 확인한 뒤** 다시 올려주세요. " +
                    "그대로 재시도하면 같은 영상이 두 번 올라갈 수 있습니다." +
                    (event.errorMessage?.let { " (사유: $it)" } ?: "")
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
        // The notification center consumes the same fields as the persisted notification.
        // Keep videoId for older clients, but also send the canonical notification fields
        // so a live upload result is readable and links to the affected video immediately.
        webSocketNotificationService.sendToUser(
            event.userId,
            type.name,
            mapOf(
                "title" to title,
                "message" to message,
                "referenceType" to "video",
                "referenceId" to event.videoId,
                "videoId" to event.videoId,
            ),
        )

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

        /*
         * **확인하지 못한 것을 실패로 확정하지 않는다.**
         *
         * 여기서 FAILED 로 적으면 아래 `markPublishFailed()` 까지 이어져 제출이 실패로
         * 굳는다. 유료 파일럿에서 그것은 **실제로 게시된 콘텐츠에 대금을 주지 않는 것**이다.
         * "거짓 실패" 가 "거짓 성공" 보다 비싼 몇 안 되는 자리다.
         *
         * 상태를 그대로 두면 제출은 PUBLISHING 에 남는다. `VideoUploadPoller` 가 이후
         * 주기에서 실제 결과를 확인하면 그때 확정된다.
         */
        if (event.outcome == UploadOutcome.UNCONFIRMED) {
            log.info(
                "게시 결과를 확인하지 못해 캠페인 게시물 상태를 확정하지 않는다. uploadId={} platform={}",
                uploadId, event.platform,
            )
            return
        }

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

        // 캠페인 게시물과 같은 이유로 확정하지 않는다. FAILED 로 적으면 이미 올라간
        // 클립을 실패로 남기고, 사용자는 그것을 보고 다시 게시해 중복을 만든다.
        if (event.outcome == UploadOutcome.UNCONFIRMED) {
            log.info(
                "게시 결과를 확인하지 못해 쇼츠 게시 상태를 확정하지 않는다. uploadId={} platform={}",
                uploadId, event.platform,
            )
            return
        }

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
