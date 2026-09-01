package com.ongo.application.ugc

import com.ongo.application.ugc.dto.CampaignPostListResponse
import com.ongo.application.ugc.dto.CampaignPostResponse
import com.ongo.application.ugc.dto.PublishRequest
import com.ongo.application.ugc.dto.RegisterExternalPostRequest
import com.ongo.common.enums.Platform
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.campaign.CampaignRepository
import com.ongo.domain.ugc.publishing.CampaignPost
import com.ongo.domain.ugc.publishing.CampaignPostRepository
import com.ongo.domain.ugc.publishing.CampaignPublishPort
import com.ongo.domain.ugc.publishing.PostStatus
import com.ongo.domain.ugc.publishing.PostType
import com.ongo.domain.ugc.submission.ContentSubmission
import com.ongo.domain.ugc.submission.SubmissionRepository
import com.ongo.domain.ugc.submission.SubmissionStatus
import com.ongo.domain.workspace.WorkspaceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI

/**
 * UGC 멀티 SNS 게시 연결 유스케이스.
 *
 * - 직접 게시(브랜드 트리거): 승인된 제출물의 영상을 크리에이터 채널로 게시하고 플랫폼별 campaign post를 남긴다.
 *   idempotency_key로 재시도 시 중복 게시를 막고, 한 플랫폼 실패가 다른 플랫폼 결과를 덮어쓰지 않는다.
 * - 외부 게시물 등록(크리에이터): 이미 게시한 SNS URL을 허용 도메인 검증 후 등록한다.
 */
@Service
class CampaignPublishingUseCase(
    private val campaignPostRepository: CampaignPostRepository,
    private val submissionRepository: SubmissionRepository,
    private val campaignRepository: CampaignRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val campaignPublishPort: CampaignPublishPort,
) {

    @Transactional
    fun publishSubmission(userId: Long, workspaceId: Long, submissionId: Long, request: PublishRequest): CampaignPostListResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val submission = loadSubmissionInWorkspace(workspaceId, submissionId)
        if (submission.status != SubmissionStatus.APPROVED && submission.status != SubmissionStatus.PUBLISHING) {
            throw IllegalStateException("승인된 제출물만 게시할 수 있습니다 (현재: ${submission.status})")
        }
        require(request.platforms.isNotEmpty()) { "게시할 플랫폼을 하나 이상 선택하세요" }

        val videoId = submission.assets.firstOrNull { it.assetType == "VIDEO" && it.resourceId != null }?.resourceId
            ?: throw IllegalStateException("게시할 영상 첨부가 없습니다. 외부 게시물 등록을 사용하세요.")

        // 멱등성: 이미 게시된(campaign post 존재) 플랫폼은 건너뛴다.
        val newPlatforms = request.platforms.filter {
            campaignPostRepository.findByIdempotencyKey(idempotencyKey(submissionId, it)) == null
        }

        if (newPlatforms.isNotEmpty()) {
            val caption = submission.caption
            val outcomes = if (caption.isNullOrBlank()) {
                campaignPublishPort.publish(submission.creatorId, videoId, newPlatforms)
            } else {
                campaignPublishPort.publishWithMetadata(
                    creatorId = submission.creatorId,
                    videoId = videoId,
                    platforms = newPlatforms,
                    title = caption.lineSequence().firstOrNull()?.take(100),
                    description = caption,
                )
            }
            outcomes.forEach { outcome ->
                campaignPostRepository.save(
                        CampaignPost(
                        campaignId = submission.campaignId,
                        submissionId = submissionId,
                        creatorId = submission.creatorId,
                        platform = outcome.platform,
                        postType = PostType.DIRECT,
                        videoUploadId = outcome.videoUploadId,
                        platformPostId = outcome.platformPostId,
                        status = when (outcome.status.uppercase()) {
                            "PUBLISHED" -> PostStatus.PUBLISHED
                            "FAILED" -> PostStatus.FAILED
                            else -> PostStatus.PUBLISHING
                        },
                        idempotencyKey = idempotencyKey(submissionId, outcome.platform),
                        errorMessage = outcome.errorMessage,
                    ),
                )
            }
            if (submission.status == SubmissionStatus.APPROVED) {
                submissionRepository.updateStatus(submission.markPublishing())
            }
            reconcileImmediateCompletion(submissionId)
        }

        return CampaignPostListResponse(campaignPostRepository.findBySubmissionId(submissionId).map { it.toResponse() })
    }

    private fun reconcileImmediateCompletion(submissionId: Long) {
        val current = submissionRepository.findById(submissionId) ?: return
        if (current.status != SubmissionStatus.PUBLISHING) return
        val posts = campaignPostRepository.findBySubmissionId(submissionId)
            .filter { it.postType == PostType.DIRECT }
        when {
            posts.any { it.status == PostStatus.FAILED } ->
                submissionRepository.updateStatus(current.markPublishFailed())
            posts.isNotEmpty() && posts.all { it.status == PostStatus.PUBLISHED } ->
                submissionRepository.updateStatus(current.markPublished())
        }
    }

    @Transactional
    fun registerExternalPost(creatorId: Long, submissionId: Long, request: RegisterExternalPostRequest): CampaignPostResponse {
        val submission = submissionRepository.findById(submissionId) ?: throw NotFoundException("제출", submissionId)
        if (submission.creatorId != creatorId) throw NotFoundException("제출", submissionId)
        if (submission.status !in APPROVED_OR_LATER) {
            throw IllegalStateException("승인 후에만 외부 게시물을 등록할 수 있습니다 (현재: ${submission.status})")
        }
        val platform = Platform.valueOf(request.platform)
        if (!isAllowedExternalUrl(platform, request.externalPostUrl)) {
            throw IllegalArgumentException("${platform.name}에 허용되지 않은 URL입니다")
        }

        val key = "ext:$submissionId:${request.platform}:${request.platformPostId ?: request.externalPostUrl}"
        val saved = campaignPostRepository.save(
            CampaignPost(
                campaignId = submission.campaignId,
                submissionId = submissionId,
                creatorId = creatorId,
                platform = request.platform,
                postType = PostType.EXTERNAL,
                externalPostUrl = request.externalPostUrl,
                platformPostId = request.platformPostId,
                status = PostStatus.EXTERNAL,
                idempotencyKey = key,
            ),
        )
        return saved.toResponse()
    }

    fun listCampaignPosts(userId: Long, workspaceId: Long, campaignId: Long): CampaignPostListResponse {
        assertWorkspaceAccess(userId, workspaceId)
        loadCampaignInWorkspace(workspaceId, campaignId)
        return CampaignPostListResponse(campaignPostRepository.findByCampaignId(campaignId).map { it.toResponse() })
    }

    fun listMySubmissionPosts(creatorId: Long, submissionId: Long): CampaignPostListResponse {
        val submission = submissionRepository.findById(submissionId) ?: throw NotFoundException("제출", submissionId)
        if (submission.creatorId != creatorId) throw NotFoundException("제출", submissionId)
        return CampaignPostListResponse(campaignPostRepository.findBySubmissionId(submissionId).map { it.toResponse() })
    }

    // ---- 헬퍼 ----

    private fun idempotencyKey(submissionId: Long, platform: String) = "sub:$submissionId:plat:$platform"

    private fun isAllowedExternalUrl(platform: Platform, url: String): Boolean {
        val host = try {
            URI(url).takeIf { it.scheme.equals("https", ignoreCase = true) }
                ?.host
                ?.lowercase()
                ?: return false
        } catch (_: Exception) {
            return false
        }
        // 플랫폼 매핑이 빠진 경우에도 임의의 URL을 외부 게시물로 저장하면 안 된다.
        // 매핑 누락은 설정/구현 누락이지 검증을 우회할 이유가 아니므로 fail-closed 한다.
        val allowed = ALLOWED_HOSTS[platform] ?: return false
        return allowed.any { host == it || host.endsWith(".$it") }
    }

    private fun assertWorkspaceAccess(userId: Long, workspaceId: Long) {
        val accessible = workspaceRepository.findAccessibleByUserId(userId).any { it.id == workspaceId }
        if (!accessible) throw NotFoundException("워크스페이스", workspaceId)
    }

    private fun loadCampaignInWorkspace(workspaceId: Long, campaignId: Long) {
        val campaign = campaignRepository.findById(campaignId) ?: throw NotFoundException("캠페인", campaignId)
        if (campaign.workspaceId != workspaceId) throw NotFoundException("캠페인", campaignId)
    }

    private fun loadSubmissionInWorkspace(workspaceId: Long, submissionId: Long): ContentSubmission {
        val submission = submissionRepository.findById(submissionId) ?: throw NotFoundException("제출", submissionId)
        loadCampaignInWorkspace(workspaceId, submission.campaignId)
        return submission
    }

    private fun CampaignPost.toResponse(): CampaignPostResponse = CampaignPostResponse(
        id = id!!,
        campaignId = campaignId,
        submissionId = submissionId,
        creatorId = creatorId,
        platform = platform,
        postType = postType.name,
        videoUploadId = videoUploadId,
        externalPostUrl = externalPostUrl,
        platformPostId = platformPostId,
        status = status.name,
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        private val APPROVED_OR_LATER = setOf(
            SubmissionStatus.APPROVED,
            SubmissionStatus.PUBLISHING,
            SubmissionStatus.PUBLISHED,
        )

        private val ALLOWED_HOSTS: Map<Platform, List<String>> = mapOf(
            Platform.YOUTUBE to listOf("youtube.com", "youtu.be"),
            Platform.TIKTOK to listOf("tiktok.com"),
            Platform.INSTAGRAM to listOf("instagram.com"),
            Platform.NAVER_CLIP to listOf("naver.com"),
            Platform.TWITTER to listOf("twitter.com", "x.com"),
            Platform.FACEBOOK to listOf("facebook.com", "fb.watch"),
            Platform.THREADS to listOf("threads.net"),
            Platform.PINTEREST to listOf("pinterest.com"),
            Platform.LINKEDIN to listOf("linkedin.com"),
            // MVP 외부 게시물 등록은 WordPress.com 주소만 허용한다.
            // self-hosted WordPress는 URL만으로 소유 플랫폼을 검증할 수 없어 별도 검증 없이는 허용하지 않는다.
            Platform.WORDPRESS to listOf("wordpress.com"),
            Platform.TUMBLR to listOf("tumblr.com"),
            Platform.VIMEO to listOf("vimeo.com"),
            Platform.DAILYMOTION to listOf("dailymotion.com"),
        )
    }
}
