package com.ongo.application.video

import com.ongo.application.notification.WebSocketNotificationService
import com.ongo.common.enums.Platform
import com.ongo.domain.notification.Notification
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
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * **"확인하지 못함" 을 "실패" 로 접지 않는지** 고정한다.
 *
 * ## 무엇이 깨져 있었나
 *
 * `UploadCompletedEvent.success` 가 `Boolean` 이라 결과 셋(게시됨·실패함·확인 못 함)이
 * 둘로 접혔다. `VideoUploadPoller` 와 `VideoPublishEventListener` 는 결과를 확인하지
 * 못했을 때 DB 를 `UNCONFIRMED` 로 남긴다 — 없는 게시물을 있다고 말하지 않기 위해서다.
 * 그런데 이벤트는 그것을 곧바로 "실패" 로 바꿔 내보냈다.
 *
 * `VideoPublishEventListener` 의 주석이 그 위험을 정확히 적어 두고도 바로 다음 줄에서
 * 같은 일을 하고 있었다:
 *
 * > 무조건 FAILED로 기록하면 사용자가 재시도하여 중복 게시를 만들 수 있으므로
 * > 확인 불가 상태로 남기고 …
 *
 * ## 무엇이 비쌌나
 *
 * 1. 사용자는 "업로드 실패" 알림을 보고 **다시 올린다** → 플랫폼에 중복 게시물.
 *    되돌리는 것은 사용자 몫이고, 중복은 도달률에도 해가 된다.
 * 2. UGC 캠페인 제출이 `markPublishFailed()` 로 굳는다 → **유료 파일럿에서
 *    실제로 게시된 콘텐츠에 대금을 주지 않는 것**이 된다.
 *
 * 유료 UGC 에서는 "거짓 실패" 가 "거짓 성공" 보다 비싸다.
 */
class UnconfirmedUploadOutcomeTest {

    private val notifications = mockk<NotificationRepository>(relaxed = true)
    private val websocket = mockk<WebSocketNotificationService>(relaxed = true)
    private val campaignPosts = mockk<CampaignPostRepository>(relaxed = true)
    private val submissions = mockk<SubmissionRepository>(relaxed = true)
    private val publications = mockk<ClipPublicationRepository>(relaxed = true)

    private val listener = UploadCompletedEventListener(
        notifications, websocket, campaignPosts, submissions, publications,
    )

    private val uploadId = 10L

    private fun stubUgcRows() {
        val post = CampaignPost(
            id = 1L,
            campaignId = 2L,
            submissionId = 3L,
            creatorId = 4L,
            platform = "YOUTUBE#77",
            postType = PostType.DIRECT,
            videoUploadId = uploadId,
            status = PostStatus.PUBLISHING,
            idempotencyKey = "key",
        )
        every { campaignPosts.findByVideoUploadId(uploadId) } returns listOf(post)
        every { campaignPosts.findBySubmissionId(3L) } returns listOf(post)
        every { submissions.findById(3L) } returns ContentSubmission(
            id = 3L,
            campaignId = 2L,
            creatorId = 4L,
            status = SubmissionStatus.PUBLISHING,
        )
        every { publications.findByVideoUploadId(uploadId) } returns listOf(
            ClipPublication(
                id = 20L,
                clipId = 30L,
                platform = "YOUTUBE#77",
                videoUploadId = uploadId,
                status = ClipPublicationStatus.SCHEDULED,
            ),
        )
    }

    private fun unconfirmedEvent() = UploadCompletedEvent(
        videoId = 5L,
        userId = 4L,
        platform = Platform.TIKTOK,
        outcome = UploadOutcome.UNCONFIRMED,
        errorMessage = "timeout",
        videoUploadId = uploadId,
    )

    // ------------------------------------------------------------------ 알림 문구

    /**
     * **핵심 회귀.** "실패" 라고 말하면 사용자가 다시 올려 중복을 만든다.
     */
    @Test
    @DisplayName("확인 불가 알림이 실패라고 단정하지 않는다")
    fun unconfirmedNotificationDoesNotClaimFailure() {
        stubUgcRows()
        val saved = slot<Notification>()
        every { notifications.save(capture(saved)) } answers { firstArg() }

        listener.handleUploadCompleted(unconfirmedEvent())

        val notification = saved.captured
        assertTrue(
            "확인" in notification.title,
            "제목이 확인 필요를 말하지 않는다: ${notification.title}",
        )
        assertTrue(
            "실패" !in notification.title,
            "확인하지 못한 것을 실패라고 단정했다: ${notification.title}",
        )
    }

    /**
     * 그냥 "모른다" 로 끝내면 사용자는 결국 다시 올린다. **무엇을 해야 하는지**까지
     * 말해야 중복 게시를 막는다.
     */
    @Test
    @DisplayName("재시도 전에 채널을 확인하라고 안내한다")
    fun unconfirmedNotificationWarnsAboutDuplicate() {
        stubUgcRows()
        val saved = slot<Notification>()
        every { notifications.save(capture(saved)) } answers { firstArg() }

        listener.handleUploadCompleted(unconfirmedEvent())

        val message = saved.captured.message
        assertTrue("확인" in message, "채널 확인 안내가 없다: $message")
        assertTrue(
            "두 번" in message || "중복" in message,
            "중복 게시 위험을 알리지 않는다: $message",
        )
    }

    // ------------------------------------------------------------------ UGC 정산

    /**
     * **가장 비싼 회귀.** 제출이 실패로 굳으면 이미 게시된 콘텐츠에 대금을 주지 않는다.
     */
    @Test
    @DisplayName("확인 불가는 캠페인 게시물을 실패로 확정하지 않는다")
    fun unconfirmedDoesNotFailCampaignPost() {
        stubUgcRows()

        listener.handleUploadCompleted(unconfirmedEvent())

        verify(exactly = 0) {
            campaignPosts.updateStatus(any(), PostStatus.FAILED, any(), any())
        }
    }

    /** 제출 상태 자체도 건드리지 않아야 한다 — PUBLISHING 에 남아 재확인 대상이 된다. */
    @Test
    @DisplayName("확인 불가는 제출을 게시 실패로 표시하지 않는다")
    fun unconfirmedDoesNotMarkSubmissionFailed() {
        stubUgcRows()

        listener.handleUploadCompleted(unconfirmedEvent())

        verify(exactly = 0) { submissions.updateStatus(any()) }
    }

    /** 쇼츠 게시도 같다. FAILED 로 남기면 사용자가 다시 게시해 중복이 된다. */
    @Test
    @DisplayName("확인 불가는 쇼츠 게시를 실패로 기록하지 않는다")
    fun unconfirmedDoesNotFailShortsPublication() {
        stubUgcRows()

        listener.handleUploadCompleted(unconfirmedEvent())

        verify(exactly = 0) { publications.update(any()) }
    }

    // ------------------------------------------------------------------ 진짜 실패

    /**
     * **확인 불가를 살리면서 진짜 실패까지 무시하면 더 나쁘다.** 플랫폼이 거절한 것은
     * 게시물이 없다는 것을 아는 상태이므로 그대로 실패로 확정해야 한다.
     */
    @Test
    @DisplayName("플랫폼이 거절한 실패는 그대로 확정한다")
    fun definiteFailureStillReconciles() {
        stubUgcRows()

        listener.handleUploadCompleted(
            UploadCompletedEvent(
                videoId = 5L,
                userId = 4L,
                platform = Platform.TIKTOK,
                outcome = UploadOutcome.FAILED,
                errorMessage = "provider rejected",
                videoUploadId = uploadId,
            ),
        )

        verify { campaignPosts.updateStatus(any(), PostStatus.FAILED, any(), any()) }
        verify { publications.update(any()) }
    }

    /** 성공 경로가 살아 있는지도 함께 본다 — 확인 불가 처리가 성공을 막으면 안 된다. */
    @Test
    @DisplayName("게시 확인은 그대로 반영한다")
    fun publishedStillReconciles() {
        stubUgcRows()

        listener.handleUploadCompleted(
            UploadCompletedEvent(
                videoId = 5L,
                userId = 4L,
                platform = Platform.TIKTOK,
                outcome = UploadOutcome.PUBLISHED,
                platformPostId = "post-1",
                videoUploadId = uploadId,
            ),
        )

        verify { campaignPosts.updateStatus(any(), PostStatus.PUBLISHED, any(), any()) }
        verify { publications.update(any()) }
    }

    // ------------------------------------------------------------------ 계약

    /**
     * `success` 는 **게시를 확인했는가**만 뜻한다. `!success` 를 "실패" 로 읽는 코드가
     * 생기면 이 수정이 그대로 되돌아간다.
     */
    @Test
    @DisplayName("success 는 확인된 게시에만 참이다")
    fun successMeansConfirmedPublish() {
        assertTrue(UploadOutcome.PUBLISHED.isPublished)
        assertTrue(!UploadOutcome.UNCONFIRMED.isPublished)
        assertTrue(!UploadOutcome.FAILED.isPublished)

        // 실패 여부는 별도로 판단한다 — 이 구분이 이 수정의 전부다.
        assertTrue(UploadOutcome.FAILED.isDefiniteFailure)
        assertTrue(
            !UploadOutcome.UNCONFIRMED.isDefiniteFailure,
            "확인하지 못한 것을 확정 실패로 분류하면 중복 게시와 정산 누락이 되돌아온다",
        )
    }
}
