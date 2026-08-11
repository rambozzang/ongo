package com.ongo.application.ugc

import com.ongo.application.ugc.dto.PublishRequest
import com.ongo.application.ugc.dto.RegisterExternalPostRequest
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.campaign.Campaign
import com.ongo.domain.ugc.campaign.CampaignRepository
import com.ongo.domain.ugc.publishing.CampaignPost
import com.ongo.domain.ugc.publishing.CampaignPostRepository
import com.ongo.domain.ugc.publishing.CampaignPublishPort
import com.ongo.domain.ugc.publishing.PlatformPublishOutcome
import com.ongo.domain.ugc.publishing.PostStatus
import com.ongo.domain.ugc.publishing.PostType
import com.ongo.domain.ugc.submission.ContentSubmission
import com.ongo.domain.ugc.submission.SubmissionAsset
import com.ongo.domain.ugc.submission.SubmissionRepository
import com.ongo.domain.ugc.submission.SubmissionStatus
import com.ongo.domain.workspace.Workspace
import com.ongo.domain.workspace.WorkspaceRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class CampaignPublishingUseCaseTest {

    @MockK lateinit var campaignPostRepository: CampaignPostRepository
    @MockK lateinit var submissionRepository: SubmissionRepository
    @MockK lateinit var campaignRepository: CampaignRepository
    @MockK lateinit var workspaceRepository: WorkspaceRepository
    @MockK lateinit var campaignPublishPort: CampaignPublishPort

    @InjectMockKs lateinit var useCase: CampaignPublishingUseCase

    private val userId = 1L
    private val workspaceId = 10L
    private val campaignId = 50L
    private val submissionId = 500L
    private val creatorId = 100L
    private val videoId = 7L

    private fun grantAccess(vararg ids: Long) {
        every { workspaceRepository.findAccessibleByUserId(userId) } returns
            ids.map { Workspace(id = it, ownerId = userId, name = "w$it", slug = "w$it") }
    }

    private fun campaign() = Campaign(id = campaignId, workspaceId = workspaceId, name = "c", createdBy = 1)

    private fun submission(status: SubmissionStatus = SubmissionStatus.APPROVED, withVideo: Boolean = true) =
        ContentSubmission(
            id = submissionId, campaignId = campaignId, creatorId = creatorId, status = status,
            assets = if (withVideo) listOf(SubmissionAsset(assetType = "VIDEO", resourceType = "video", resourceId = videoId)) else emptyList(),
        )

    // ---- publish ----

    @Test
    fun `publish fails when submission is not approved`() {
        grantAccess(workspaceId)
        every { submissionRepository.findById(submissionId) } returns submission(status = SubmissionStatus.SUBMITTED)
        every { campaignRepository.findById(campaignId) } returns campaign()
        assertFailsWith<IllegalStateException> {
            useCase.publishSubmission(userId, workspaceId, submissionId, PublishRequest(listOf("YOUTUBE")))
        }
    }

    @Test
    fun `publish fails when there is no video asset`() {
        grantAccess(workspaceId)
        every { submissionRepository.findById(submissionId) } returns submission(withVideo = false)
        every { campaignRepository.findById(campaignId) } returns campaign()
        assertFailsWith<IllegalStateException> {
            useCase.publishSubmission(userId, workspaceId, submissionId, PublishRequest(listOf("YOUTUBE")))
        }
    }

    @Test
    fun `publish calls the port and records posts, marking the submission publishing`() {
        grantAccess(workspaceId)
        every { submissionRepository.findById(submissionId) } returns submission()
        every { campaignRepository.findById(campaignId) } returns campaign()
        every { campaignPostRepository.findByIdempotencyKey(any()) } returns null
        every { campaignPublishPort.publish(creatorId, videoId, listOf("YOUTUBE")) } returns
            listOf(PlatformPublishOutcome("YOUTUBE", 111, "UPLOADING"))
        val saved = mutableListOf<CampaignPost>()
        every { campaignPostRepository.save(capture(saved)) } answers { firstArg() }
        every { submissionRepository.updateStatus(any()) } answers { firstArg() }
        every { campaignPostRepository.findBySubmissionId(submissionId) } returns emptyList()

        useCase.publishSubmission(userId, workspaceId, submissionId, PublishRequest(listOf("YOUTUBE")))

        assertEquals(PostType.DIRECT, saved[0].postType)
        assertEquals(PostStatus.PUBLISHING, saved[0].status)
        assertEquals(111, saved[0].videoUploadId)
        verify(exactly = 1) { campaignPublishPort.publish(creatorId, videoId, listOf("YOUTUBE")) }
        verify(exactly = 1) { submissionRepository.updateStatus(any()) }
    }

    @Test
    fun `immediate provider completion is persisted as published`() {
        grantAccess(workspaceId)
        var currentSubmission = submission()
        every { submissionRepository.findById(submissionId) } answers { currentSubmission }
        every { campaignRepository.findById(campaignId) } returns campaign()
        every { campaignPostRepository.findByIdempotencyKey(any()) } returns null
        every { campaignPublishPort.publish(creatorId, videoId, listOf("YOUTUBE")) } returns
            listOf(PlatformPublishOutcome("YOUTUBE", 111, "PUBLISHED"))
        val saved = mutableListOf<CampaignPost>()
        every { campaignPostRepository.save(any()) } answers {
            firstArg<CampaignPost>().copy(id = 1L).also(saved::add)
        }
        every { campaignPostRepository.findBySubmissionId(submissionId) } answers { saved.toList() }
        every { submissionRepository.updateStatus(any()) } answers {
            currentSubmission = firstArg()
            currentSubmission
        }

        useCase.publishSubmission(userId, workspaceId, submissionId, PublishRequest(listOf("YOUTUBE")))

        assertEquals(PostStatus.PUBLISHED, saved.single().status)
        assertEquals(SubmissionStatus.PUBLISHED, currentSubmission.status)
    }

    @Test
    fun `publish is idempotent - already-posted platforms are skipped`() {
        grantAccess(workspaceId)
        every { submissionRepository.findById(submissionId) } returns submission()
        every { campaignRepository.findById(campaignId) } returns campaign()
        every { campaignPostRepository.findByIdempotencyKey("sub:$submissionId:plat:YOUTUBE") } returns
            CampaignPost(id = 9, campaignId = campaignId, submissionId = submissionId, creatorId = creatorId, platform = "YOUTUBE", postType = PostType.DIRECT, status = PostStatus.PUBLISHING, idempotencyKey = "sub:$submissionId:plat:YOUTUBE")
        every { campaignPostRepository.findBySubmissionId(submissionId) } returns emptyList()

        useCase.publishSubmission(userId, workspaceId, submissionId, PublishRequest(listOf("YOUTUBE")))

        verify(exactly = 0) { campaignPublishPort.publish(any(), any(), any()) }
        verify(exactly = 0) { campaignPostRepository.save(any()) }
    }

    @Test
    fun `publish preserves per-platform results on partial failure`() {
        grantAccess(workspaceId)
        every { submissionRepository.findById(submissionId) } returns submission()
        every { campaignRepository.findById(campaignId) } returns campaign()
        every { campaignPostRepository.findByIdempotencyKey(any()) } returns null
        every { campaignPublishPort.publish(creatorId, videoId, listOf("YOUTUBE", "TIKTOK")) } returns listOf(
            PlatformPublishOutcome("YOUTUBE", 111, "UPLOADING"),
            PlatformPublishOutcome("TIKTOK", null, "FAILED", "token expired"),
        )
        val saved = mutableListOf<CampaignPost>()
        every { campaignPostRepository.save(capture(saved)) } answers { firstArg() }
        every { submissionRepository.updateStatus(any()) } answers { firstArg() }
        every { campaignPostRepository.findBySubmissionId(submissionId) } returns emptyList()

        useCase.publishSubmission(userId, workspaceId, submissionId, PublishRequest(listOf("YOUTUBE", "TIKTOK")))

        assertEquals(PostStatus.PUBLISHING, saved.first { it.platform == "YOUTUBE" }.status)
        assertEquals(PostStatus.FAILED, saved.first { it.platform == "TIKTOK" }.status)
    }

    // ---- external post ----

    @Test
    fun `external post is blocked for non-owner`() {
        every { submissionRepository.findById(submissionId) } returns submission().copy(creatorId = 999)
        assertFailsWith<NotFoundException> {
            useCase.registerExternalPost(creatorId, submissionId, RegisterExternalPostRequest("YOUTUBE", "https://youtube.com/watch?v=x"))
        }
    }

    @Test
    fun `external post rejects a disallowed url`() {
        every { submissionRepository.findById(submissionId) } returns submission()
        assertFailsWith<IllegalArgumentException> {
            useCase.registerExternalPost(creatorId, submissionId, RegisterExternalPostRequest("YOUTUBE", "https://evil.example/x"))
        }
    }

    @Test
    fun `external post is registered for an allowed url`() {
        every { submissionRepository.findById(submissionId) } returns submission()
        every { campaignPostRepository.save(any()) } answers { firstArg<CampaignPost>().copy(id = 1) }

        val result = useCase.registerExternalPost(
            creatorId, submissionId,
            RegisterExternalPostRequest("YOUTUBE", "https://www.youtube.com/watch?v=abc", "abc"),
        )

        assertEquals("EXTERNAL", result.postType)
        assertEquals("YOUTUBE", result.platform)
    }

    @Test
    fun `listCampaignPosts is blocked with 404 without workspace access`() {
        grantAccess()
        assertFailsWith<NotFoundException> { useCase.listCampaignPosts(userId, workspaceId, campaignId) }
    }
}
