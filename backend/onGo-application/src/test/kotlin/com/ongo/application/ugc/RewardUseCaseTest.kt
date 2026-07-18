package com.ongo.application.ugc

import com.ongo.application.ugc.dto.UpdateRewardRequest
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.campaign.Campaign
import com.ongo.domain.ugc.campaign.CampaignRepository
import com.ongo.domain.ugc.participation.CampaignParticipant
import com.ongo.domain.ugc.participation.ParticipantRepository
import com.ongo.domain.ugc.reward.RewardConfirmation
import com.ongo.domain.ugc.reward.RewardRepository
import com.ongo.domain.ugc.reward.RewardStatus
import com.ongo.domain.workspace.Workspace
import com.ongo.domain.workspace.WorkspaceRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class RewardUseCaseTest {

    @MockK lateinit var rewardRepository: RewardRepository
    @MockK lateinit var participantRepository: ParticipantRepository
    @MockK lateinit var campaignRepository: CampaignRepository
    @MockK lateinit var workspaceRepository: WorkspaceRepository

    @InjectMockKs lateinit var useCase: RewardUseCase

    private val userId = 1L
    private val workspaceId = 10L
    private val campaignId = 50L
    private val participantId = 500L
    private val creatorId = 100L

    private fun grantAccess(vararg ids: Long) {
        every { workspaceRepository.findAccessibleByUserId(userId) } returns
            ids.map { Workspace(id = it, ownerId = userId, name = "w$it", slug = "w$it") }
    }

    private fun campaign(budget: Long = 1_000_000) =
        Campaign(id = campaignId, workspaceId = workspaceId, name = "c", totalBudget = budget, createdBy = 1)

    private fun participant() =
        CampaignParticipant(id = participantId, campaignId = campaignId, creatorId = creatorId, agreedReward = 100_000)

    private fun reward(total: Long = 100_000, status: RewardStatus = RewardStatus.DRAFT, note: String? = null) =
        RewardConfirmation(
            id = 9, participantId = participantId, campaignId = campaignId, creatorId = creatorId,
            baseAmount = total, bonusAmount = 0, totalAmount = total, status = status, note = note,
        )

    // ---- budget guard ----

    @Test
    fun `confirm is blocked when settled total would exceed budget`() {
        grantAccess(workspaceId)
        every { participantRepository.findById(participantId) } returns participant()
        every { campaignRepository.findById(campaignId) } returns campaign(budget = 100_000)
        every { rewardRepository.findByParticipantId(participantId) } returns reward(total = 80_000)
        every { rewardRepository.sumSettledTotalByCampaign(campaignId) } returns 50_000 // 50k + 80k > 100k

        assertFailsWith<IllegalStateException> { useCase.confirmReward(userId, workspaceId, participantId) }
    }

    @Test
    fun `confirm succeeds within budget`() {
        grantAccess(workspaceId)
        every { participantRepository.findById(participantId) } returns participant()
        every { campaignRepository.findById(campaignId) } returns campaign(budget = 100_000)
        every { rewardRepository.findByParticipantId(participantId) } returns reward(total = 30_000)
        every { rewardRepository.sumSettledTotalByCampaign(campaignId) } returns 50_000 // 50k + 30k <= 100k
        val saved = slot<RewardConfirmation>()
        every { rewardRepository.update(capture(saved)) } answers { saved.captured }

        val result = useCase.confirmReward(userId, workspaceId, participantId)

        assertEquals(RewardStatus.CONFIRMED, saved.captured.status)
        assertEquals("CONFIRMED", result.status)
    }

    // ---- update / markPaid ----

    @Test
    fun `update creates a draft reward computing total`() {
        grantAccess(workspaceId)
        every { participantRepository.findById(participantId) } returns participant()
        every { campaignRepository.findById(campaignId) } returns campaign()
        every { rewardRepository.findByParticipantId(participantId) } returns null
        val saved = slot<RewardConfirmation>()
        every { rewardRepository.save(capture(saved)) } answers { saved.captured.copy(id = 9) }

        val result = useCase.updateReward(userId, workspaceId, participantId, UpdateRewardRequest(baseAmount = 90_000, bonusAmount = 10_000))

        assertEquals(100_000, saved.captured.totalAmount)
        assertEquals(100_000, result.totalAmount)
    }

    @Test
    fun `update on a confirmed reward fails`() {
        grantAccess(workspaceId)
        every { participantRepository.findById(participantId) } returns participant()
        every { campaignRepository.findById(campaignId) } returns campaign()
        every { rewardRepository.findByParticipantId(participantId) } returns reward(status = RewardStatus.CONFIRMED)

        assertFailsWith<IllegalStateException> {
            useCase.updateReward(userId, workspaceId, participantId, UpdateRewardRequest(baseAmount = 1))
        }
    }

    @Test
    fun `mark paid moves confirmed to paid`() {
        grantAccess(workspaceId)
        every { participantRepository.findById(participantId) } returns participant()
        every { campaignRepository.findById(campaignId) } returns campaign()
        every { rewardRepository.findByParticipantId(participantId) } returns reward(status = RewardStatus.CONFIRMED)
        val saved = slot<RewardConfirmation>()
        every { rewardRepository.update(capture(saved)) } answers { saved.captured }

        useCase.markPaid(userId, workspaceId, participantId)

        assertEquals(RewardStatus.PAID_EXTERNALLY, saved.captured.status)
    }

    // ---- CSV ----

    @Test
    fun `csv export escapes formula injection and includes BOM`() {
        grantAccess(workspaceId)
        every { campaignRepository.findById(campaignId) } returns campaign()
        every { participantRepository.findByCampaignId(campaignId) } returns listOf(participant())
        every { rewardRepository.findByParticipantId(participantId) } returns reward(note = "=SUM(A1:A9)")

        val csv = useCase.exportRewardsCsv(userId, workspaceId, campaignId).toString(Charsets.UTF_8)

        assertTrue(csv.startsWith("﻿"), "should start with UTF-8 BOM")
        assertTrue(csv.contains("'=SUM(A1:A9)"), "leading = must be guarded")
    }

    @Test
    fun `list is blocked with 404 without workspace access`() {
        grantAccess()
        assertFailsWith<NotFoundException> { useCase.listParticipantRewards(userId, workspaceId, campaignId) }
    }
}
