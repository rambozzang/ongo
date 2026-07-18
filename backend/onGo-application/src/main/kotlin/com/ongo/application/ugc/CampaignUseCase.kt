package com.ongo.application.ugc

import com.ongo.application.ugc.dto.CampaignDetailResponse
import com.ongo.application.ugc.dto.CampaignListResponse
import com.ongo.application.ugc.dto.CampaignResponse
import com.ongo.application.ugc.dto.CreateCampaignRequest
import com.ongo.application.ugc.dto.PlaybookResponse
import com.ongo.application.ugc.dto.PlaybookStepResponse
import com.ongo.application.ugc.dto.UpdateCampaignRequest
import com.ongo.application.ugc.dto.UpsertPlaybookRequest
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.campaign.Campaign
import com.ongo.domain.ugc.campaign.CampaignRepository
import com.ongo.domain.ugc.campaign.CampaignStatus
import com.ongo.domain.ugc.campaign.Playbook
import com.ongo.domain.ugc.campaign.PlaybookRepository
import com.ongo.domain.ugc.campaign.PlaybookStep
import com.ongo.domain.workspace.WorkspaceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * UGC 캠페인 유스케이스.
 *
 * 인가 원칙: 모든 진입점에서 `userId`가 대상 `workspaceId`에 접근 가능한지 먼저 검증하고,
 * 캠페인이 해당 워크스페이스 소유인지 확인한다. 접근 불가/불일치는 존재를 드러내지 않도록
 * 404([NotFoundException])로 처리한다.
 */
@Service
class CampaignUseCase(
    private val campaignRepository: CampaignRepository,
    private val playbookRepository: PlaybookRepository,
    private val workspaceRepository: WorkspaceRepository,
) {

    fun listCampaigns(
        userId: Long,
        workspaceId: Long,
        status: String?,
        query: String?,
        page: Int,
        size: Int,
    ): CampaignListResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val safeSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val safePage = page.coerceAtLeast(0)
        val offset = safePage * safeSize

        val items = campaignRepository
            .findByWorkspaceId(workspaceId, status, query, offset, safeSize)
            .map { it.toResponse() }
        val total = campaignRepository.countByWorkspaceId(workspaceId, status, query)

        return CampaignListResponse(items = items, totalElements = total, page = safePage, size = safeSize)
    }

    fun getCampaign(userId: Long, workspaceId: Long, campaignId: Long): CampaignDetailResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val campaign = loadCampaignInWorkspace(workspaceId, campaignId)
        return toDetail(campaign, playbookRepository.findByCampaignId(campaignId))
    }

    @Transactional
    fun createCampaign(userId: Long, workspaceId: Long, request: CreateCampaignRequest): CampaignDetailResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val campaign = Campaign(
            workspaceId = workspaceId,
            name = request.name,
            description = request.description,
            status = CampaignStatus.DRAFT,
            objective = request.objective,
            totalBudget = request.totalBudget,
            currency = request.currency,
            fixedRewardPerCreator = request.fixedRewardPerCreator,
            startAt = request.startAt,
            endAt = request.endAt,
            createdBy = userId,
        )
        return toDetail(campaignRepository.save(campaign), null)
    }

    @Transactional
    fun updateCampaign(
        userId: Long,
        workspaceId: Long,
        campaignId: Long,
        request: UpdateCampaignRequest,
    ): CampaignDetailResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val campaign = loadCampaignInWorkspace(workspaceId, campaignId)
        campaign.assertEditable()

        // copy()는 data class init 불변식(예산/기간/이름)을 다시 검증한다.
        val updated = campaign.copy(
            name = request.name ?: campaign.name,
            description = request.description ?: campaign.description,
            objective = request.objective ?: campaign.objective,
            totalBudget = request.totalBudget ?: campaign.totalBudget,
            currency = request.currency ?: campaign.currency,
            fixedRewardPerCreator = request.fixedRewardPerCreator ?: campaign.fixedRewardPerCreator,
            startAt = request.startAt ?: campaign.startAt,
            endAt = request.endAt ?: campaign.endAt,
        )
        val saved = campaignRepository.update(updated)
        return toDetail(saved, playbookRepository.findByCampaignId(campaignId))
    }

    @Transactional
    fun publishCampaign(userId: Long, workspaceId: Long, campaignId: Long): CampaignDetailResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val campaign = loadCampaignInWorkspace(workspaceId, campaignId)
        val published = campaign.publish(hasActivePlaybook = playbookRepository.existsByCampaignId(campaignId))
        val saved = campaignRepository.update(published)
        return toDetail(saved, playbookRepository.findByCampaignId(campaignId))
    }

    @Transactional
    fun pauseCampaign(userId: Long, workspaceId: Long, campaignId: Long): CampaignDetailResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val campaign = loadCampaignInWorkspace(workspaceId, campaignId)
        val saved = campaignRepository.update(campaign.pause())
        return toDetail(saved, playbookRepository.findByCampaignId(campaignId))
    }

    @Transactional
    fun completeCampaign(userId: Long, workspaceId: Long, campaignId: Long): CampaignDetailResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val campaign = loadCampaignInWorkspace(workspaceId, campaignId)
        val saved = campaignRepository.update(campaign.complete())
        return toDetail(saved, playbookRepository.findByCampaignId(campaignId))
    }

    @Transactional
    fun upsertPlaybook(
        userId: Long,
        workspaceId: Long,
        campaignId: Long,
        request: UpsertPlaybookRequest,
    ): PlaybookResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val campaign = loadCampaignInWorkspace(workspaceId, campaignId)
        if (campaign.status.isTerminal()) {
            throw IllegalStateException("종료된 캠페인의 플레이북은 수정할 수 없습니다")
        }

        val playbook = Playbook(
            campaignId = campaignId,
            title = request.title,
            summary = request.summary,
            contentType = request.contentType,
            steps = request.steps.mapIndexed { index, step ->
                PlaybookStep(
                    sortOrder = index,
                    stepType = step.stepType,
                    title = step.title,
                    instruction = step.instruction,
                    exampleUrl = step.exampleUrl,
                    required = step.required,
                )
            },
        )
        return playbookRepository.upsert(playbook).toResponse()
    }

    // ---- 인가 헬퍼 ----

    private fun assertWorkspaceAccess(userId: Long, workspaceId: Long) {
        val accessible = workspaceRepository.findAccessibleByUserId(userId).any { it.id == workspaceId }
        if (!accessible) throw NotFoundException("워크스페이스", workspaceId)
    }

    private fun loadCampaignInWorkspace(workspaceId: Long, campaignId: Long): Campaign {
        val campaign = campaignRepository.findById(campaignId) ?: throw NotFoundException("캠페인", campaignId)
        if (campaign.workspaceId != workspaceId) throw NotFoundException("캠페인", campaignId)
        return campaign
    }

    // ---- 매핑 ----

    private fun toDetail(campaign: Campaign, playbook: Playbook?): CampaignDetailResponse =
        CampaignDetailResponse(campaign.toResponse(), playbook?.toResponse())

    private fun Campaign.toResponse(): CampaignResponse = CampaignResponse(
        id = id!!,
        workspaceId = workspaceId,
        name = name,
        description = description,
        status = status.name,
        objective = objective,
        totalBudget = totalBudget,
        currency = currency,
        fixedRewardPerCreator = fixedRewardPerCreator,
        startAt = startAt,
        endAt = endAt,
        createdBy = createdBy,
        createdAt = createdAt,
        updatedAt = updatedAt,
        version = version,
    )

    private fun Playbook.toResponse(): PlaybookResponse = PlaybookResponse(
        id = id!!,
        campaignId = campaignId,
        title = title,
        summary = summary,
        contentType = contentType,
        revision = revision,
        steps = steps.map {
            PlaybookStepResponse(
                sortOrder = it.sortOrder,
                stepType = it.stepType,
                title = it.title,
                instruction = it.instruction,
                exampleUrl = it.exampleUrl,
                required = it.required,
            )
        },
    )

    companion object {
        private const val MAX_PAGE_SIZE = 100
    }
}
