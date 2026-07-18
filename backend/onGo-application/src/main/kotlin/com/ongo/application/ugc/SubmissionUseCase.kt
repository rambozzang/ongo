package com.ongo.application.ugc

import com.ongo.application.ugc.dto.CreateSubmissionRequest
import com.ongo.application.ugc.dto.ReviewDecisionRequest
import com.ongo.application.ugc.dto.ReviewResponse
import com.ongo.application.ugc.dto.SubmissionAssetDto
import com.ongo.application.ugc.dto.SubmissionDetailResponse
import com.ongo.application.ugc.dto.SubmissionListResponse
import com.ongo.application.ugc.dto.SubmissionResponse
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
import com.ongo.domain.workspace.WorkspaceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * UGC 제출·검수 유스케이스.
 *
 * 크리에이터: 참여자만 제출 가능, 초안/수정요청 상태에서만 수정, 재제출 시 revision 증가.
 * 브랜드: 워크스페이스 인가 + 캠페인 소유 검증. 모든 검수 판단(사용자·시각·사유)을 이력으로 남긴다.
 */
@Service
class SubmissionUseCase(
    private val submissionRepository: SubmissionRepository,
    private val reviewRepository: SubmissionReviewRepository,
    private val participantRepository: ParticipantRepository,
    private val campaignRepository: CampaignRepository,
    private val workspaceRepository: WorkspaceRepository,
) {

    // ---- 크리에이터 ----

    @Transactional
    fun createOrUpdateSubmission(creatorId: Long, campaignId: Long, request: CreateSubmissionRequest): SubmissionResponse {
        if (!participantRepository.existsByCampaignIdAndCreatorId(campaignId, creatorId)) {
            throw ForbiddenException("수락된 참여자만 콘텐츠를 제출할 수 있습니다")
        }
        val assets = request.assets.map { it.toDomain() }
        val existing = submissionRepository.findByCampaignIdAndCreatorId(campaignId, creatorId)

        val result = if (existing == null) {
            submissionRepository.save(
                ContentSubmission(campaignId = campaignId, creatorId = creatorId, caption = request.caption, assets = assets),
            )
        } else {
            existing.assertEditable()
            // 수정요청 상태에서 다시 편집하면 새 revision(초안)으로 되돌린다.
            val nextRevision = if (existing.status == SubmissionStatus.CHANGES_REQUESTED) existing.revision + 1 else existing.revision
            submissionRepository.update(
                existing.copy(
                    caption = request.caption,
                    assets = assets,
                    revision = nextRevision,
                    status = SubmissionStatus.DRAFT,
                ),
            )
        }
        return result.toResponse()
    }

    fun listMySubmissions(creatorId: Long, campaignId: Long): SubmissionListResponse {
        val items = submissionRepository.findByCampaignIdAndCreatorId(campaignId, creatorId)
            ?.let { listOf(it.toResponse()) } ?: emptyList()
        return SubmissionListResponse(items = items, totalElements = items.size.toLong(), page = 0, size = items.size)
    }

    @Transactional
    fun submitSubmission(creatorId: Long, submissionId: Long): SubmissionResponse {
        val submission = submissionRepository.findById(submissionId) ?: throw NotFoundException("제출", submissionId)
        if (submission.creatorId != creatorId) throw NotFoundException("제출", submissionId)
        val submitted = submission.submit().copy(submittedAt = LocalDateTime.now())
        return submissionRepository.updateStatus(submitted).toResponse()
    }

    // ---- 브랜드 ----

    fun listSubmissions(
        userId: Long,
        workspaceId: Long,
        campaignId: Long,
        status: String?,
        page: Int,
        size: Int,
    ): SubmissionListResponse {
        assertWorkspaceAccess(userId, workspaceId)
        loadCampaignInWorkspace(workspaceId, campaignId)
        val safeSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val safePage = page.coerceAtLeast(0)
        val items = submissionRepository.findByCampaignId(campaignId, status, safePage * safeSize, safeSize).map { it.toResponse() }
        val total = submissionRepository.countByCampaignId(campaignId, status)
        return SubmissionListResponse(items = items, totalElements = total, page = safePage, size = safeSize)
    }

    fun getSubmissionDetail(userId: Long, workspaceId: Long, submissionId: Long): SubmissionDetailResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val submission = loadSubmissionInWorkspace(workspaceId, submissionId)
        val reviews = reviewRepository.findBySubmissionId(submissionId).map { it.toResponse() }
        return SubmissionDetailResponse(submission.toResponse(), reviews)
    }

    @Transactional
    fun requestChanges(userId: Long, workspaceId: Long, submissionId: Long, request: ReviewDecisionRequest): SubmissionResponse {
        val comment = request.comment?.trim()
        if (comment.isNullOrBlank()) throw IllegalArgumentException("수정 요청에는 사유가 필요합니다")
        assertWorkspaceAccess(userId, workspaceId)
        val submission = loadSubmissionInWorkspace(workspaceId, submissionId)

        val updated = submissionRepository.updateStatus(submission.requestChanges())
        reviewRepository.save(
            SubmissionReview(submissionId = submissionId, reviewerId = userId, decision = "CHANGES_REQUESTED", comment = comment),
        )
        return updated.toResponse()
    }

    @Transactional
    fun approveSubmission(userId: Long, workspaceId: Long, submissionId: Long, request: ReviewDecisionRequest): SubmissionResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val submission = loadSubmissionInWorkspace(workspaceId, submissionId)

        val approved = submission.approve().copy(approvedAt = LocalDateTime.now())
        val updated = submissionRepository.updateStatus(approved)
        reviewRepository.save(
            SubmissionReview(submissionId = submissionId, reviewerId = userId, decision = "APPROVED", comment = request.comment?.trim()),
        )
        return updated.toResponse()
    }

    // ---- 헬퍼 ----

    private fun assertWorkspaceAccess(userId: Long, workspaceId: Long) {
        val accessible = workspaceRepository.findAccessibleByUserId(userId).any { it.id == workspaceId }
        if (!accessible) throw NotFoundException("워크스페이스", workspaceId)
    }

    private fun loadCampaignInWorkspace(workspaceId: Long, campaignId: Long): Campaign {
        val campaign = campaignRepository.findById(campaignId) ?: throw NotFoundException("캠페인", campaignId)
        if (campaign.workspaceId != workspaceId) throw NotFoundException("캠페인", campaignId)
        return campaign
    }

    private fun loadSubmissionInWorkspace(workspaceId: Long, submissionId: Long): ContentSubmission {
        val submission = submissionRepository.findById(submissionId) ?: throw NotFoundException("제출", submissionId)
        loadCampaignInWorkspace(workspaceId, submission.campaignId)
        return submission
    }

    private fun SubmissionAssetDto.toDomain(): SubmissionAsset {
        if (externalUrl != null && !externalUrl.startsWith("https://") && !externalUrl.startsWith("http://")) {
            throw IllegalArgumentException("외부 URL은 http(s) 스킴만 허용됩니다")
        }
        return SubmissionAsset(assetType = assetType, resourceType = resourceType, resourceId = resourceId, externalUrl = externalUrl)
    }

    private fun ContentSubmission.toResponse(): SubmissionResponse = SubmissionResponse(
        id = id!!,
        campaignId = campaignId,
        creatorId = creatorId,
        revision = revision,
        caption = caption,
        status = status.name,
        submittedAt = submittedAt,
        approvedAt = approvedAt,
        assets = assets.map { SubmissionAssetDto(it.assetType, it.resourceType, it.resourceId, it.externalUrl) },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun SubmissionReview.toResponse(): ReviewResponse = ReviewResponse(
        id = id!!,
        reviewerId = reviewerId,
        decision = decision,
        comment = comment,
        createdAt = createdAt,
    )

    companion object {
        private const val MAX_PAGE_SIZE = 100
    }
}
