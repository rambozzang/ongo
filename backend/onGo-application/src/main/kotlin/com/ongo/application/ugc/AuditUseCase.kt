package com.ongo.application.ugc

import com.ongo.application.ugc.dto.AuditEventListResponse
import com.ongo.application.ugc.dto.AuditEventResponse
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.audit.AuditEvent
import com.ongo.domain.ugc.audit.AuditEventRepository
import com.ongo.domain.ugc.campaign.CampaignRepository
import com.ongo.domain.workspace.WorkspaceRepository
import org.springframework.stereotype.Service

/**
 * 운영자용 감사 로그 조회. 워크스페이스 접근 + 캠페인 소유를 검증한다.
 */
@Service
class AuditUseCase(
    private val auditEventRepository: AuditEventRepository,
    private val campaignRepository: CampaignRepository,
    private val workspaceRepository: WorkspaceRepository,
) {

    fun listByCampaign(userId: Long, workspaceId: Long, campaignId: Long, page: Int, size: Int): AuditEventListResponse {
        assertWorkspaceAccess(userId, workspaceId)
        loadCampaignInWorkspace(workspaceId, campaignId)

        val safeSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val safePage = page.coerceAtLeast(0)
        val items = auditEventRepository.findByCampaignId(campaignId, safePage * safeSize, safeSize).map { it.toResponse() }
        val total = auditEventRepository.countByCampaignId(campaignId)
        return AuditEventListResponse(items = items, totalElements = total, page = safePage, size = safeSize)
    }

    private fun assertWorkspaceAccess(userId: Long, workspaceId: Long) {
        val accessible = workspaceRepository.findAccessibleByUserId(userId).any { it.id == workspaceId }
        if (!accessible) throw NotFoundException("워크스페이스", workspaceId)
    }

    private fun loadCampaignInWorkspace(workspaceId: Long, campaignId: Long) {
        val campaign = campaignRepository.findById(campaignId) ?: throw NotFoundException("캠페인", campaignId)
        if (campaign.workspaceId != workspaceId) throw NotFoundException("캠페인", campaignId)
    }

    private fun AuditEvent.toResponse(): AuditEventResponse = AuditEventResponse(
        id = id!!,
        actorId = actorId,
        action = action,
        resourceType = resourceType,
        resourceId = resourceId,
        detail = detail,
        createdAt = createdAt,
    )

    companion object {
        private const val MAX_PAGE_SIZE = 100
    }
}
