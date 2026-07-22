package com.ongo.application.ugc

import com.ongo.application.ugc.dto.CreateCampaignRequest
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.campaign.Campaign
import com.ongo.domain.ugc.campaign.CampaignRepository
import com.ongo.domain.ugc.campaign.CampaignStatus
import com.ongo.domain.ugc.campaign.PlaybookRepository
import com.ongo.domain.workspace.Workspace
import com.ongo.domain.workspace.WorkspaceRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class CampaignUseCaseTest {

    @MockK
    lateinit var campaignRepository: CampaignRepository

    @MockK
    lateinit var playbookRepository: PlaybookRepository

    @MockK
    lateinit var workspaceRepository: WorkspaceRepository

    @InjectMockKs
    lateinit var useCase: CampaignUseCase

    private val userId = 1L
    private val workspaceId = 10L
    private val otherWorkspaceId = 99L

    private fun grantAccess(vararg accessibleIds: Long) {
        every { workspaceRepository.findAccessibleByUserId(userId) } returns
            accessibleIds.map { Workspace(id = it, ownerId = userId, name = "WS $it", slug = "ws-$it") }
    }

    private fun campaign(
        id: Long = 100L,
        wsId: Long = workspaceId,
        status: CampaignStatus = CampaignStatus.DRAFT,
    ) = Campaign(
        id = id,
        workspaceId = wsId,
        name = "여름 캠페인",
        status = status,
        totalBudget = 1_000_000,
        fixedRewardPerCreator = 100_000,
        startAt = LocalDateTime.of(2026, 8, 1, 0, 0),
        endAt = LocalDateTime.of(2026, 8, 31, 0, 0),
        createdBy = userId,
    )

    @Test
    fun `list is blocked with 404 when user has no access to the workspace`() {
        grantAccess() // 접근 가능한 워크스페이스 없음
        assertFailsWith<NotFoundException> {
            useCase.listCampaigns(userId, workspaceId, null, null, 0, 20)
        }
    }

    @Test
    fun `get is blocked with 404 when campaign belongs to another workspace (id tampering)`() {
        grantAccess(workspaceId)
        every { campaignRepository.findById(100L) } returns campaign(id = 100L, wsId = otherWorkspaceId)
        assertFailsWith<NotFoundException> {
            useCase.getCampaign(userId, workspaceId, 100L)
        }
    }

    @Test
    fun `get returns detail when accessible and campaign is in the workspace`() {
        grantAccess(workspaceId)
        every { campaignRepository.findById(100L) } returns campaign(id = 100L)
        every { playbookRepository.findByCampaignId(100L) } returns null

        val result = useCase.getCampaign(userId, workspaceId, 100L)

        assertEquals(100L, result.campaign.id)
        assertEquals("DRAFT", result.campaign.status)
    }

    @Test
    fun `create is blocked with 404 when user can only access a different workspace`() {
        grantAccess(otherWorkspaceId)
        assertFailsWith<NotFoundException> {
            useCase.createCampaign(userId, workspaceId, CreateCampaignRequest(name = "새 캠페인"))
        }
    }

    @Test
    fun `create persists campaign as DRAFT owned by the target workspace`() {
        grantAccess(workspaceId)
        val captured = slot<Campaign>()
        every { campaignRepository.save(capture(captured)) } answers { campaign(id = 100L) }

        val result = useCase.createCampaign(
            userId,
            workspaceId,
            CreateCampaignRequest(name = "새 캠페인", totalBudget = 500_000),
        )

        assertEquals(workspaceId, captured.captured.workspaceId)
        assertEquals(CampaignStatus.DRAFT, captured.captured.status)
        assertEquals(userId, captured.captured.createdBy)
        assertEquals(100L, result.campaign.id)
    }

    @Test
    fun `publish fails when the campaign has no active playbook`() {
        grantAccess(workspaceId)
        every { campaignRepository.findById(100L) } returns campaign(id = 100L)
        every { playbookRepository.existsByCampaignId(100L) } returns false

        assertFailsWith<IllegalStateException> {
            useCase.publishCampaign(userId, workspaceId, 100L)
        }
    }

    @Test
    fun `publish transitions DRAFT to RECRUITING when a playbook exists`() {
        grantAccess(workspaceId)
        every { campaignRepository.findById(100L) } returns campaign(id = 100L)
        every { playbookRepository.existsByCampaignId(100L) } returns true
        every { playbookRepository.findByCampaignId(100L) } returns null
        val captured = slot<Campaign>()
        every { campaignRepository.update(capture(captured)) } answers { captured.captured }

        val result = useCase.publishCampaign(userId, workspaceId, 100L)

        assertEquals(CampaignStatus.RECRUITING, captured.captured.status)
        assertEquals("RECRUITING", result.campaign.status)
    }
}
