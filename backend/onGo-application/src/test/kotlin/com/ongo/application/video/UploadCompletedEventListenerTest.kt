package com.ongo.application.video

import com.ongo.common.enums.NotificationType
import com.ongo.common.enums.Platform
import com.ongo.domain.notification.NotificationRepository
import com.ongo.domain.ugc.publishing.CampaignPost
import com.ongo.domain.ugc.publishing.CampaignPostRepository
import com.ongo.domain.ugc.publishing.PostStatus
import com.ongo.domain.ugc.publishing.PostType
import com.ongo.domain.ugc.shorts.ClipPublication
import com.ongo.domain.ugc.shorts.ClipPublicationRepository
import com.ongo.domain.ugc.shorts.ClipPublicationStatus
import com.ongo.domain.ugc.submission.ContentSubmission
import com.ongo.domain.ugc.submission.SubmissionRepository
import com.ongo.domain.ugc.submission.SubmissionStatus
import com.ongo.application.notification.WebSocketNotificationService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class UploadCompletedEventListenerTest {
    private val notifications = mockk<NotificationRepository>(relaxed = true)
    private val websocket = mockk<WebSocketNotificationService>(relaxed = true)
    private val campaignPosts = mockk<CampaignPostRepository>(relaxed = true)
    private val submissions = mockk<SubmissionRepository>(relaxed = true)
    private val publications = mockk<ClipPublicationRepository>(relaxed = true)

    private val listener = UploadCompletedEventListener(
        notifications,
        websocket,
        campaignPosts,
        submissions,
        publications,
    )

    @Test
    fun `confirmed upload reconciles UGC campaign and shorts publication`() {
        val post = CampaignPost(
            id = 1L,
            campaignId = 2L,
            submissionId = 3L,
            creatorId = 4L,
            platform = "YOUTUBE#77",
            postType = PostType.DIRECT,
            videoUploadId = 10L,
            status = PostStatus.PUBLISHING,
            idempotencyKey = "key",
        )
        val submission = ContentSubmission(
            id = 3L,
            campaignId = 2L,
            creatorId = 4L,
            status = SubmissionStatus.PUBLISHING,
        )
        val publication = ClipPublication(
            id = 20L,
            clipId = 30L,
            platform = "YOUTUBE#77",
            videoUploadId = 10L,
            status = ClipPublicationStatus.SCHEDULED,
        )
        every { campaignPosts.findByVideoUploadId(10L) } returns listOf(post)
        every { campaignPosts.findBySubmissionId(3L) } returns listOf(post.copy(status = PostStatus.PUBLISHED))
        every { submissions.findById(3L) } returns submission
        every { publications.findByVideoUploadId(10L) } returns listOf(publication)

        listener.handleUploadCompleted(
            UploadCompletedEvent(
                videoId = 5L,
                userId = 4L,
                platform = Platform.YOUTUBE,
                success = true,
                platformUrl = "https://youtube.com/watch?v=abc",
                videoUploadId = 10L,
            ),
        )

        verify {
            campaignPosts.updateStatus(1L, PostStatus.PUBLISHED, null, null)
            submissions.updateStatus(match { it.status == SubmissionStatus.PUBLISHED })
            publications.update(match {
                it.id == 20L && it.status == ClipPublicationStatus.PUBLISHED && it.publishedAt != null
            })
            notifications.save(match { it.type == NotificationType.UPLOAD_COMPLETE })
        }
    }

    @Test
    fun `failed upload marks campaign and shorts publication failed`() {
        val post = CampaignPost(
            id = 1L,
            campaignId = 2L,
            submissionId = 3L,
            creatorId = 4L,
            platform = "TIKTOK#88",
            postType = PostType.DIRECT,
            videoUploadId = 10L,
            status = PostStatus.PUBLISHING,
            idempotencyKey = "key",
        )
        val submission = ContentSubmission(
            id = 3L,
            campaignId = 2L,
            creatorId = 4L,
            status = SubmissionStatus.PUBLISHING,
        )
        val publication = ClipPublication(
            id = 20L,
            clipId = 30L,
            platform = "TIKTOK#88",
            videoUploadId = 10L,
            status = ClipPublicationStatus.SCHEDULED,
        )
        every { campaignPosts.findByVideoUploadId(10L) } returns listOf(post)
        every { campaignPosts.findBySubmissionId(3L) } returns listOf(post.copy(status = PostStatus.FAILED))
        every { submissions.findById(3L) } returns submission
        every { publications.findByVideoUploadId(10L) } returns listOf(publication)

        listener.handleUploadCompleted(
            UploadCompletedEvent(
                videoId = 5L,
                userId = 4L,
                platform = Platform.TIKTOK,
                success = false,
                errorMessage = "provider rejected",
                videoUploadId = 10L,
            ),
        )

        verify {
            campaignPosts.updateStatus(1L, PostStatus.FAILED, null, "provider rejected")
            submissions.updateStatus(match { it.status == SubmissionStatus.PUBLISH_FAILED })
            publications.update(match {
                it.id == 20L && it.status == ClipPublicationStatus.FAILED && it.errorMessage == "provider rejected"
            })
            notifications.save(match { it.type == NotificationType.UPLOAD_FAILED })
        }
    }
}
