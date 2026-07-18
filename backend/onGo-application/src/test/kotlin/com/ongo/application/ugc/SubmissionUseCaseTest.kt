package com.ongo.application.ugc

import com.ongo.application.ugc.dto.CreateSubmissionRequest
import com.ongo.application.ugc.dto.ReviewDecisionRequest
import com.ongo.application.ugc.dto.SubmissionAssetDto
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.campaign.Campaign
import com.ongo.domain.ugc.campaign.CampaignRepository
import com.ongo.domain.ugc.participation.ParticipantRepository
import com.ongo.domain.ugc.submission.ContentSubmission
import com.ongo.domain.ugc.submission.SubmissionAsset
import com.ongo.domain.ugc.submission.SubmissionRepository
import com.ongo.domain.ugc.submission.SubmissionReview
import com.ongo.domain.ugc.submission.SubmissionReviewRepository
import com.ongo.domain.ugc.submission.SubmissionStatus
import com.ongo.domain.workspace.Workspace
import com.ongo.domain.workspace.WorkspaceRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@ExtendWith(MockKExtension::class)
class SubmissionUseCaseTest {

    @MockK lateinit var submissionRepository: SubmissionRepository
    @MockK lateinit var reviewRepository: SubmissionReviewRepository
    @MockK lateinit var participantRepository: ParticipantRepository
    @MockK lateinit var campaignRepository: CampaignRepository
    @MockK lateinit var workspaceRepository: WorkspaceRepository

    @InjectMockKs lateinit var useCase: SubmissionUseCase

    private val creatorId = 100L
    private val userId = 1L
    private val workspaceId = 10L
    private val otherWorkspaceId = 99L
    private val campaignId = 50L
    private val submissionId = 500L

    private fun grantAccess(vararg ids: Long) {
        every { workspaceRepository.findAccessibleByUserId(userId) } returns
            ids.map { Workspace(id = it, ownerId = userId, name = "w$it", slug = "w$it") }
    }

    private fun campaign(wsId: Long = workspaceId) =
        Campaign(id = campaignId, workspaceId = wsId, name = "캠페인", createdBy = 1)

    private fun asset() = SubmissionAsset(assetType = "EXTERNAL", externalUrl = "https://x/y")

    private fun submission(status: SubmissionStatus = SubmissionStatus.DRAFT, revision: Int = 1) =
        ContentSubmission(
            id = submissionId, campaignId = campaignId, creatorId = creatorId,
            revision = revision, caption = "cap", status = status, assets = listOf(asset()),
        )

    // ---- creator ----

    @Test
    fun `create is forbidden for a non-participant`() {
        every { participantRepository.existsByCampaignIdAndCreatorId(campaignId, creatorId) } returns false
        assertFailsWith<ForbiddenException> {
            useCase.createOrUpdateSubmission(creatorId, campaignId, CreateSubmissionRequest(caption = "c"))
        }
    }

    @Test
    fun `create persists a new draft for a participant`() {
        every { participantRepository.existsByCampaignIdAndCreatorId(campaignId, creatorId) } returns true
        every { submissionRepository.findByCampaignIdAndCreatorId(campaignId, creatorId) } returns null
        val saved = slot<ContentSubmission>()
        every { submissionRepository.save(capture(saved)) } answers { saved.captured.copy(id = submissionId) }

        val result = useCase.createOrUpdateSubmission(
            creatorId, campaignId,
            CreateSubmissionRequest(caption = "안녕", assets = listOf(SubmissionAssetDto(assetType = "EXTERNAL", externalUrl = "https://a/b"))),
        )

        assertEquals(SubmissionStatus.DRAFT, saved.captured.status)
        assertEquals(1, saved.captured.revision)
        assertEquals("DRAFT", result.status)
    }

    @Test
    fun `editing a changes-requested submission bumps revision and resets to draft`() {
        every { participantRepository.existsByCampaignIdAndCreatorId(campaignId, creatorId) } returns true
        every { submissionRepository.findByCampaignIdAndCreatorId(campaignId, creatorId) } returns
            submission(status = SubmissionStatus.CHANGES_REQUESTED, revision = 2)
        val updated = slot<ContentSubmission>()
        every { submissionRepository.update(capture(updated)) } answers { updated.captured }

        useCase.createOrUpdateSubmission(creatorId, campaignId, CreateSubmissionRequest(caption = "고침"))

        assertEquals(3, updated.captured.revision)
        assertEquals(SubmissionStatus.DRAFT, updated.captured.status)
    }

    @Test
    fun `submit moves draft to submitted with a timestamp`() {
        every { submissionRepository.findById(submissionId) } returns submission()
        val updated = slot<ContentSubmission>()
        every { submissionRepository.updateStatus(capture(updated)) } answers { updated.captured }

        val result = useCase.submitSubmission(creatorId, submissionId)

        assertEquals(SubmissionStatus.SUBMITTED, updated.captured.status)
        assertNotNull(updated.captured.submittedAt)
        assertEquals("SUBMITTED", result.status)
    }

    @Test
    fun `submit by non-owner is blocked with 404`() {
        every { submissionRepository.findById(submissionId) } returns submission().copy(creatorId = 999)
        assertFailsWith<NotFoundException> { useCase.submitSubmission(creatorId, submissionId) }
    }

    // ---- brand ----

    @Test
    fun `request changes requires a non-blank reason`() {
        grantAccess(workspaceId)
        assertFailsWith<IllegalArgumentException> {
            useCase.requestChanges(userId, workspaceId, submissionId, ReviewDecisionRequest(comment = "  "))
        }
    }

    @Test
    fun `request changes records a review and flips status`() {
        grantAccess(workspaceId)
        every { submissionRepository.findById(submissionId) } returns submission(status = SubmissionStatus.SUBMITTED)
        every { campaignRepository.findById(campaignId) } returns campaign()
        val updated = slot<ContentSubmission>()
        every { submissionRepository.updateStatus(capture(updated)) } answers { updated.captured }
        val review = slot<SubmissionReview>()
        every { reviewRepository.save(capture(review)) } answers { review.captured.copy(id = 1) }

        useCase.requestChanges(userId, workspaceId, submissionId, ReviewDecisionRequest(comment = "화질 개선 필요"))

        assertEquals(SubmissionStatus.CHANGES_REQUESTED, updated.captured.status)
        assertEquals("CHANGES_REQUESTED", review.captured.decision)
        assertEquals(userId, review.captured.reviewerId)
        verify(exactly = 1) { reviewRepository.save(any()) }
    }

    @Test
    fun `approve moves submitted to approved and records a review`() {
        grantAccess(workspaceId)
        every { submissionRepository.findById(submissionId) } returns submission(status = SubmissionStatus.SUBMITTED)
        every { campaignRepository.findById(campaignId) } returns campaign()
        val updated = slot<ContentSubmission>()
        every { submissionRepository.updateStatus(capture(updated)) } answers { updated.captured }
        every { reviewRepository.save(any()) } answers { firstArg<SubmissionReview>().copy(id = 1) }

        val result = useCase.approveSubmission(userId, workspaceId, submissionId, ReviewDecisionRequest())

        assertEquals(SubmissionStatus.APPROVED, updated.captured.status)
        assertNotNull(updated.captured.approvedAt)
        assertEquals("APPROVED", result.status)
    }

    @Test
    fun `approving a draft submission fails`() {
        grantAccess(workspaceId)
        every { submissionRepository.findById(submissionId) } returns submission(status = SubmissionStatus.DRAFT)
        every { campaignRepository.findById(campaignId) } returns campaign()
        assertFailsWith<IllegalStateException> {
            useCase.approveSubmission(userId, workspaceId, submissionId, ReviewDecisionRequest())
        }
    }

    @Test
    fun `list is blocked with 404 without workspace access`() {
        grantAccess()
        assertFailsWith<NotFoundException> {
            useCase.listSubmissions(userId, workspaceId, campaignId, null, 0, 20)
        }
    }

    @Test
    fun `submission detail is blocked when campaign is in another workspace`() {
        grantAccess(workspaceId)
        every { submissionRepository.findById(submissionId) } returns submission(status = SubmissionStatus.SUBMITTED)
        every { campaignRepository.findById(campaignId) } returns campaign(wsId = otherWorkspaceId)
        assertFailsWith<NotFoundException> {
            useCase.getSubmissionDetail(userId, workspaceId, submissionId)
        }
    }
}
