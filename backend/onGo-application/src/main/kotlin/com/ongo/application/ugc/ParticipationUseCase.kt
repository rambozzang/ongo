package com.ongo.application.ugc

import com.ongo.application.ugc.dto.ApplicationListResponse
import com.ongo.application.ugc.dto.ApplicationResponse
import com.ongo.application.ugc.dto.ApplyRequest
import com.ongo.application.ugc.dto.CreateInviteRequest
import com.ongo.application.ugc.dto.InviteResponse
import com.ongo.application.ugc.dto.MyApplicationListResponse
import com.ongo.application.ugc.dto.MyApplicationResponse
import com.ongo.application.ugc.dto.PublicCampaignResponse
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.campaign.Campaign
import com.ongo.domain.ugc.campaign.CampaignRepository
import com.ongo.domain.ugc.campaign.CampaignStatus
import com.ongo.domain.ugc.campaign.PlaybookRepository
import com.ongo.domain.ugc.participation.ApplicationRepository
import com.ongo.domain.ugc.participation.CampaignApplication
import com.ongo.domain.ugc.participation.CampaignInvite
import com.ongo.domain.ugc.participation.CampaignParticipant
import com.ongo.domain.ugc.participation.InviteRepository
import com.ongo.domain.ugc.participation.ParticipantRepository
import com.ongo.domain.workspace.WorkspaceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * UGC 모집·참여 유스케이스: 초대 발급(브랜드), 토큰 기반 지원(크리에이터),
 * 지원자 관리·수락·거절(브랜드), 내 지원 목록(크리에이터).
 *
 * 인가: 브랜드 API는 워크스페이스 접근과 캠페인 소유를 검증(불일치 시 404).
 * 크리에이터 API는 인증 사용자 본인(userId=creatorId) 기준으로 동작한다.
 */
@Service
class ParticipationUseCase(
    private val applicationRepository: ApplicationRepository,
    private val participantRepository: ParticipantRepository,
    private val inviteRepository: InviteRepository,
    private val campaignRepository: CampaignRepository,
    private val playbookRepository: PlaybookRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val inviteTokenService: InviteTokenService,
) {

    // ---- 브랜드: 초대 ----

    @Transactional
    fun createInvite(userId: Long, workspaceId: Long, campaignId: Long, request: CreateInviteRequest): InviteResponse {
        assertWorkspaceAccess(userId, workspaceId)
        loadCampaignInWorkspace(workspaceId, campaignId)

        val rawToken = inviteTokenService.generateToken()
        val invite = CampaignInvite(
            campaignId = campaignId,
            tokenHash = inviteTokenService.hash(rawToken),
            expiresAt = request.expiresInDays?.let { LocalDateTime.now().plusDays(it.toLong()) },
            maxUses = request.maxUses,
            createdBy = userId,
        )
        return inviteRepository.save(invite).toResponse(rawToken)
    }

    // ---- 크리에이터: 토큰으로 캠페인 보기 / 지원 ----

    fun getCampaignByToken(userId: Long, token: String): PublicCampaignResponse {
        val invite = usableInviteOrThrow(token)
        val campaign = campaignRepository.findById(invite.campaignId)
            ?: throw NotFoundException("캠페인", invite.campaignId)
        val campaignId = campaign.id!!
        val playbook = playbookRepository.findByCampaignId(campaignId)
        val alreadyApplied = applicationRepository.findByCampaignIdAndCreatorId(campaignId, userId) != null

        return PublicCampaignResponse(
            campaignId = campaignId,
            name = campaign.name,
            description = campaign.description,
            objective = campaign.objective,
            status = campaign.status.name,
            startAt = campaign.startAt,
            endAt = campaign.endAt,
            currency = campaign.currency,
            fixedRewardPerCreator = campaign.fixedRewardPerCreator,
            playbookTitle = playbook?.title,
            playbookSummary = playbook?.summary,
            alreadyApplied = alreadyApplied,
        )
    }

    @Transactional
    fun apply(userId: Long, token: String, request: ApplyRequest): ApplicationResponse {
        val invite = usableInviteOrThrow(token)
        val campaign = campaignRepository.findById(invite.campaignId)
            ?: throw NotFoundException("캠페인", invite.campaignId)
        val campaignId = campaign.id!!
        if (campaign.status != CampaignStatus.RECRUITING && campaign.status != CampaignStatus.ACTIVE) {
            throw IllegalStateException("지금은 지원할 수 없는 캠페인입니다 (상태: ${campaign.status})")
        }
        if (applicationRepository.findByCampaignIdAndCreatorId(campaignId, userId) != null) {
            throw IllegalStateException("이미 지원한 캠페인입니다")
        }

        val saved = applicationRepository.save(
            CampaignApplication(
                campaignId = campaignId,
                creatorId = userId,
                message = request.message,
                portfolioUrl = request.portfolioUrl,
            ),
        )
        inviteRepository.incrementUsedCount(invite.id!!)
        return saved.toResponse()
    }

    fun myApplications(userId: Long, page: Int, size: Int): MyApplicationListResponse {
        val safeSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val safePage = page.coerceAtLeast(0)
        val apps = applicationRepository.findByCreatorId(userId, safePage * safeSize, safeSize)
        val total = applicationRepository.countByCreatorId(userId)

        val items = apps.map { app ->
            val campaign = campaignRepository.findById(app.campaignId)
            MyApplicationResponse(
                application = app.toResponse(),
                campaignName = campaign?.name ?: "-",
                campaignStatus = campaign?.status?.name ?: "-",
                startAt = campaign?.startAt,
                endAt = campaign?.endAt,
            )
        }
        return MyApplicationListResponse(items = items, totalElements = total, page = safePage, size = safeSize)
    }

    // ---- 브랜드: 지원자 목록/수락/거절 ----

    fun listApplications(
        userId: Long,
        workspaceId: Long,
        campaignId: Long,
        status: String?,
        page: Int,
        size: Int,
    ): ApplicationListResponse {
        assertWorkspaceAccess(userId, workspaceId)
        loadCampaignInWorkspace(workspaceId, campaignId)

        val safeSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val safePage = page.coerceAtLeast(0)
        val items = applicationRepository
            .findByCampaignId(campaignId, status, safePage * safeSize, safeSize)
            .map { it.toResponse() }
        val total = applicationRepository.countByCampaignId(campaignId, status)
        return ApplicationListResponse(items = items, totalElements = total, page = safePage, size = safeSize)
    }

    /** 수락: 지원 상태 전이 + 참여자 생성을 하나의 트랜잭션으로 처리한다. */
    @Transactional
    fun acceptApplication(userId: Long, workspaceId: Long, applicationId: Long): ApplicationResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val application = applicationRepository.findById(applicationId)
            ?: throw NotFoundException("지원", applicationId)
        val campaign = loadCampaignInWorkspace(workspaceId, application.campaignId)
        val campaignId = campaign.id!!

        val updated = applicationRepository.updateStatus(application.accept(userId))
        if (!participantRepository.existsByCampaignIdAndCreatorId(campaignId, application.creatorId)) {
            participantRepository.save(
                CampaignParticipant(
                    campaignId = campaignId,
                    creatorId = application.creatorId,
                    agreedReward = campaign.fixedRewardPerCreator,
                ),
            )
        }
        return updated.toResponse()
    }

    @Transactional
    fun rejectApplication(userId: Long, workspaceId: Long, applicationId: Long): ApplicationResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val application = applicationRepository.findById(applicationId)
            ?: throw NotFoundException("지원", applicationId)
        loadCampaignInWorkspace(workspaceId, application.campaignId)
        return applicationRepository.updateStatus(application.reject(userId)).toResponse()
    }

    // ---- 헬퍼 ----

    private fun usableInviteOrThrow(token: String): CampaignInvite {
        val invite = inviteRepository.findByTokenHash(inviteTokenService.hash(token))
            ?: throw NotFoundException("초대", token)
        if (!invite.isUsable(LocalDateTime.now())) {
            throw IllegalStateException("만료되었거나 사용할 수 없는 초대 링크입니다")
        }
        return invite
    }

    private fun assertWorkspaceAccess(userId: Long, workspaceId: Long) {
        val accessible = workspaceRepository.findAccessibleByUserId(userId).any { it.id == workspaceId }
        if (!accessible) throw NotFoundException("워크스페이스", workspaceId)
    }

    private fun loadCampaignInWorkspace(workspaceId: Long, campaignId: Long): Campaign {
        val campaign = campaignRepository.findById(campaignId) ?: throw NotFoundException("캠페인", campaignId)
        if (campaign.workspaceId != workspaceId) throw NotFoundException("캠페인", campaignId)
        return campaign
    }

    private fun CampaignInvite.toResponse(rawToken: String?): InviteResponse = InviteResponse(
        id = id!!,
        campaignId = campaignId,
        token = rawToken,
        expiresAt = expiresAt,
        maxUses = maxUses,
        usedCount = usedCount,
        active = active,
    )

    private fun CampaignApplication.toResponse(): ApplicationResponse = ApplicationResponse(
        id = id!!,
        campaignId = campaignId,
        creatorId = creatorId,
        message = message,
        portfolioUrl = portfolioUrl,
        status = status.name,
        decidedBy = decidedBy,
        decidedAt = decidedAt,
        createdAt = createdAt,
    )

    companion object {
        private const val MAX_PAGE_SIZE = 100
    }
}
