package com.ongo.application.ugc

import com.ongo.application.ugc.dto.ParticipantRewardListResponse
import com.ongo.application.ugc.dto.ParticipantRewardResponse
import com.ongo.application.ugc.dto.UpdateRewardRequest
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.campaign.Campaign
import com.ongo.domain.ugc.campaign.CampaignRepository
import com.ongo.domain.ugc.participation.CampaignParticipant
import com.ongo.domain.ugc.participation.ParticipantRepository
import com.ongo.domain.ugc.reward.RewardConfirmation
import com.ongo.domain.ugc.reward.RewardRepository
import com.ongo.domain.ugc.reward.RewardStatus
import com.ongo.domain.workspace.WorkspaceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * UGC 보상 확정·지급 표시·CSV 내보내기.
 *
 * - 금액 수정은 DRAFT에서만 가능.
 * - 확정(CONFIRMED) 시 캠페인 확정 총액이 예산을 넘으면 차단.
 * - 실화폐 자동 송금은 하지 않고 외부 지급 완료(PAID_EXTERNALLY) 표시 + CSV 내보내기만 제공.
 */
@Service
class RewardUseCase(
    private val rewardRepository: RewardRepository,
    private val participantRepository: ParticipantRepository,
    private val campaignRepository: CampaignRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val auditRecorder: AuditRecorder,
) {

    fun listParticipantRewards(userId: Long, workspaceId: Long, campaignId: Long): ParticipantRewardListResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val campaign = loadCampaignInWorkspace(workspaceId, campaignId)
        val items = participantRepository.findByCampaignId(campaignId).map { toResponse(it) }
        val settled = rewardRepository.sumSettledTotalByCampaign(campaignId)
        return ParticipantRewardListResponse(
            items = items,
            totalBudget = campaign.totalBudget,
            settledTotal = settled,
            remaining = campaign.totalBudget - settled,
        )
    }

    @Transactional
    fun updateReward(userId: Long, workspaceId: Long, participantId: Long, request: UpdateRewardRequest): ParticipantRewardResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val participant = loadParticipantInWorkspace(workspaceId, participantId)

        val existing = rewardRepository.findByParticipantId(participantId)
        val saved = if (existing == null) {
            rewardRepository.save(
                RewardConfirmation(
                    participantId = participantId,
                    campaignId = participant.campaignId,
                    creatorId = participant.creatorId,
                ).withAmounts(request.baseAmount, request.bonusAmount, request.note),
            )
        } else {
            rewardRepository.update(existing.withAmounts(request.baseAmount, request.bonusAmount, request.note))
        }
        return toResponse(participant, saved)
    }

    @Transactional
    fun confirmReward(userId: Long, workspaceId: Long, participantId: Long): ParticipantRewardResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val participant = loadParticipantInWorkspace(workspaceId, participantId)
        val campaign = loadCampaignInWorkspace(workspaceId, participant.campaignId)
        val reward = rewardRepository.findByParticipantId(participantId)
            ?: throw IllegalStateException("먼저 보상 금액을 입력하세요")

        val settled = rewardRepository.sumSettledTotalByCampaign(campaign.id!!)
        if (settled + reward.totalAmount > campaign.totalBudget) {
            throw IllegalStateException(
                "확정 총액이 캠페인 예산을 초과합니다 (예산 ${campaign.totalBudget}, 기확정 $settled, 이번 ${reward.totalAmount})",
            )
        }
        val confirmed = rewardRepository.update(reward.confirm(userId))
        auditRecorder.record(
            workspaceId = workspaceId, campaignId = campaign.id, actorId = userId,
            action = "REWARD_CONFIRMED", resourceType = "reward", resourceId = confirmed.id,
            detail = "total=${confirmed.totalAmount}",
        )
        return toResponse(participant, confirmed)
    }

    @Transactional
    fun markPaid(userId: Long, workspaceId: Long, participantId: Long): ParticipantRewardResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val participant = loadParticipantInWorkspace(workspaceId, participantId)
        val reward = rewardRepository.findByParticipantId(participantId) ?: throw NotFoundException("보상", participantId)
        val paid = rewardRepository.update(reward.markPaid())
        auditRecorder.record(
            workspaceId = workspaceId, campaignId = participant.campaignId, actorId = userId,
            action = "REWARD_PAID", resourceType = "reward", resourceId = paid.id,
            detail = "total=${paid.totalAmount}",
        )
        return toResponse(participant, paid)
    }

    fun exportRewardsCsv(userId: Long, workspaceId: Long, campaignId: Long): ByteArray {
        assertWorkspaceAccess(userId, workspaceId)
        loadCampaignInWorkspace(workspaceId, campaignId)
        val rows = participantRepository.findByCampaignId(campaignId).map { toResponse(it) }

        val sb = StringBuilder()
        sb.append('﻿') // UTF-8 BOM (한글 Excel 호환)
        sb.append("participantId,creatorId,baseAmount,bonusAmount,totalAmount,status,note\n")
        rows.forEach { r ->
            sb.append(
                listOf(
                    r.participantId.toString(),
                    r.creatorId.toString(),
                    r.baseAmount.toString(),
                    r.bonusAmount.toString(),
                    r.totalAmount.toString(),
                    r.status,
                    csvCell(r.note ?: ""),
                ).joinToString(","),
            )
            sb.append("\n")
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    // ---- 헬퍼 ----

    /** CSV formula injection 방지 + 쉼표/따옴표/개행 이스케이프. */
    private fun csvCell(value: String): String {
        val guarded = if (value.isNotEmpty() && value.first() in DANGEROUS_PREFIXES) "'$value" else value
        return if (guarded.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${guarded.replace("\"", "\"\"")}\""
        } else {
            guarded
        }
    }

    private fun toResponse(participant: CampaignParticipant, reward: RewardConfirmation? = rewardRepository.findByParticipantId(participant.id!!)): ParticipantRewardResponse =
        ParticipantRewardResponse(
            participantId = participant.id!!,
            creatorId = participant.creatorId,
            agreedReward = participant.agreedReward,
            rewardId = reward?.id,
            baseAmount = reward?.baseAmount ?: 0,
            bonusAmount = reward?.bonusAmount ?: 0,
            totalAmount = reward?.totalAmount ?: 0,
            status = reward?.status?.name ?: RewardStatus.DRAFT.name,
            note = reward?.note,
            confirmedAt = reward?.confirmedAt,
        )

    private fun assertWorkspaceAccess(userId: Long, workspaceId: Long) {
        val accessible = workspaceRepository.findAccessibleByUserId(userId).any { it.id == workspaceId }
        if (!accessible) throw NotFoundException("워크스페이스", workspaceId)
    }

    private fun loadCampaignInWorkspace(workspaceId: Long, campaignId: Long): Campaign {
        val campaign = campaignRepository.findById(campaignId) ?: throw NotFoundException("캠페인", campaignId)
        if (campaign.workspaceId != workspaceId) throw NotFoundException("캠페인", campaignId)
        return campaign
    }

    private fun loadParticipantInWorkspace(workspaceId: Long, participantId: Long): CampaignParticipant {
        val participant = participantRepository.findById(participantId) ?: throw NotFoundException("참여자", participantId)
        loadCampaignInWorkspace(workspaceId, participant.campaignId)
        return participant
    }

    companion object {
        private val DANGEROUS_PREFIXES = charArrayOf('=', '+', '-', '@', '\t', '\r')
    }
}
