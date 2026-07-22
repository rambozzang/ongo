package com.ongo.application.ugc

import com.ongo.application.ugc.dto.ApplyRequest
import com.ongo.application.ugc.dto.CreateInviteRequest
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.campaign.Campaign
import com.ongo.domain.ugc.campaign.CampaignRepository
import com.ongo.domain.ugc.campaign.CampaignStatus
import com.ongo.domain.ugc.campaign.PlaybookRepository
import com.ongo.domain.ugc.participation.ApplicationRepository
import com.ongo.domain.ugc.participation.ApplicationStatus
import com.ongo.domain.ugc.participation.CampaignApplication
import com.ongo.domain.ugc.participation.CampaignInvite
import com.ongo.domain.ugc.participation.CampaignParticipant
import com.ongo.domain.ugc.participation.InviteRepository
import com.ongo.domain.ugc.participation.ParticipantRepository
import com.ongo.domain.workspace.Workspace
import com.ongo.domain.workspace.WorkspaceRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class ParticipationUseCaseTest {

    @MockK lateinit var applicationRepository: ApplicationRepository
    @MockK lateinit var participantRepository: ParticipantRepository
    @MockK lateinit var inviteRepository: InviteRepository
    @MockK lateinit var campaignRepository: CampaignRepository
    @MockK lateinit var playbookRepository: PlaybookRepository
    @MockK lateinit var workspaceRepository: WorkspaceRepository
    @MockK lateinit var inviteTokenService: InviteTokenService

    @InjectMockKs lateinit var useCase: ParticipationUseCase

    private val userId = 1L
    private val workspaceId = 10L
    private val otherWorkspaceId = 99L
    private val campaignId = 100L

    private fun grantAccess(vararg ids: Long) {
        every { workspaceRepository.findAccessibleByUserId(userId) } returns
            ids.map { Workspace(id = it, ownerId = userId, name = "w$it", slug = "w$it") }
    }

    private fun campaign(status: CampaignStatus = CampaignStatus.RECRUITING, wsId: Long = workspaceId) =
        Campaign(id = campaignId, workspaceId = wsId, name = "캠페인", status = status, fixedRewardPerCreator = 100_000, createdBy = 1)

    private fun invite(active: Boolean = true) =
        CampaignInvite(id = 5, campaignId = campaignId, tokenHash = "h", active = active, createdBy = 1)

    // ---- apply ----

    @Test
    fun `apply blocks an invalid token with 404`() {
        every { inviteTokenService.hash(any()) } returns "h"
        every { inviteRepository.findByTokenHash("h") } returns null
        assertFailsWith<NotFoundException> { useCase.apply(userId, "bad", ApplyRequest()) }
    }

    @Test
    fun `apply blocks an inactive invite`() {
        every { inviteTokenService.hash(any()) } returns "h"
        every { inviteRepository.findByTokenHash("h") } returns invite(active = false)
        assertFailsWith<IllegalStateException> { useCase.apply(userId, "t", ApplyRequest()) }
    }

    @Test
    fun `apply blocks when campaign is not recruiting`() {
        every { inviteTokenService.hash(any()) } returns "h"
        every { inviteRepository.findByTokenHash("h") } returns invite()
        every { campaignRepository.findById(campaignId) } returns campaign(status = CampaignStatus.DRAFT)
        assertFailsWith<IllegalStateException> { useCase.apply(userId, "t", ApplyRequest()) }
    }

    @Test
    fun `apply blocks a duplicate application`() {
        every { inviteTokenService.hash(any()) } returns "h"
        every { inviteRepository.findByTokenHash("h") } returns invite()
        every { campaignRepository.findById(campaignId) } returns campaign()
        every { applicationRepository.findByCampaignIdAndCreatorId(campaignId, userId) } returns
            CampaignApplication(id = 9, campaignId = campaignId, creatorId = userId)
        assertFailsWith<IllegalStateException> { useCase.apply(userId, "t", ApplyRequest()) }
    }

    @Test
    fun `apply succeeds and increments the invite usage`() {
        every { inviteTokenService.hash(any()) } returns "h"
        every { inviteRepository.findByTokenHash("h") } returns invite()
        every { campaignRepository.findById(campaignId) } returns campaign()
        every { applicationRepository.findByCampaignIdAndCreatorId(campaignId, userId) } returns null
        every { applicationRepository.save(any()) } returns
            CampaignApplication(id = 1, campaignId = campaignId, creatorId = userId)
        justRun { inviteRepository.incrementUsedCount(5) }

        val result = useCase.apply(userId, "t", ApplyRequest(message = "지원합니다"))

        assertEquals("APPLIED", result.status)
        verify(exactly = 1) { inviteRepository.incrementUsedCount(5) }
    }

    // ---- accept ----

    @Test
    fun `accept transitions application and creates a participant atomically`() {
        grantAccess(workspaceId)
        val applied = CampaignApplication(id = 7, campaignId = campaignId, creatorId = 200)
        every { applicationRepository.findById(7) } returns applied
        every { campaignRepository.findById(campaignId) } returns campaign()
        val updated = slot<CampaignApplication>()
        every { applicationRepository.updateStatus(capture(updated)) } answers { updated.captured }
        every { participantRepository.existsByCampaignIdAndCreatorId(campaignId, 200) } returns false
        val savedParticipant = slot<CampaignParticipant>()
        every { participantRepository.save(capture(savedParticipant)) } answers { savedParticipant.captured }

        val result = useCase.acceptApplication(userId, workspaceId, 7)

        assertEquals("ACCEPTED", result.status)
        assertEquals(ApplicationStatus.ACCEPTED, updated.captured.status)
        assertEquals(200, savedParticipant.captured.creatorId)
        assertEquals(100_000, savedParticipant.captured.agreedReward)
        verify(exactly = 1) { participantRepository.save(any()) }
    }

    @Test
    fun `accept is blocked with 404 when the application campaign is in another workspace`() {
        grantAccess(workspaceId)
        every { applicationRepository.findById(7) } returns
            CampaignApplication(id = 7, campaignId = campaignId, creatorId = 200)
        every { campaignRepository.findById(campaignId) } returns campaign(wsId = otherWorkspaceId)
        assertFailsWith<NotFoundException> { useCase.acceptApplication(userId, workspaceId, 7) }
    }

    // ---- authz / invite ----

    @Test
    fun `listApplications is blocked with 404 without workspace access`() {
        grantAccess()
        assertFailsWith<NotFoundException> {
            useCase.listApplications(userId, workspaceId, campaignId, null, 0, 20)
        }
    }

    @Test
    fun `createInvite returns the raw token exactly once`() {
        grantAccess(workspaceId)
        every { campaignRepository.findById(campaignId) } returns campaign()
        every { inviteTokenService.generateToken() } returns "raw-token"
        every { inviteTokenService.hash("raw-token") } returns "hashed"
        val saved = slot<CampaignInvite>()
        every { inviteRepository.save(capture(saved)) } answers { saved.captured.copy(id = 5) }

        val result = useCase.createInvite(userId, workspaceId, campaignId, CreateInviteRequest(maxUses = 10))

        assertEquals("raw-token", result.token)
        assertEquals("hashed", saved.captured.tokenHash)
        assertTrue(result.token!!.isNotBlank())
    }
}
