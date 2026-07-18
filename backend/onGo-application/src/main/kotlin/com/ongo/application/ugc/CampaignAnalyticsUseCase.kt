package com.ongo.application.ugc

import com.ongo.application.ugc.dto.CampaignAnalyticsResponse
import com.ongo.application.ugc.dto.PostMetricResponse
import com.ongo.application.ugc.dto.RecordMetricRequest
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.analytics.MetricSnapshot
import com.ongo.domain.ugc.analytics.MetricSnapshotRepository
import com.ongo.domain.ugc.campaign.CampaignRepository
import com.ongo.domain.ugc.publishing.CampaignPostRepository
import com.ongo.domain.workspace.WorkspaceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * UGC 캠페인 성과 조회. 게시물별 최신 지표 스냅샷을 합산하고 마지막 동기화 시각을 함께 반환한다.
 * 지표 기록(recordMetric)은 플랫폼 지표 동기화 스케줄러가 쓰는 진입점을 대신하며,
 * 실제 플랫폼 API 연동은 후속 작업이다.
 */
@Service
class CampaignAnalyticsUseCase(
    private val campaignPostRepository: CampaignPostRepository,
    private val metricSnapshotRepository: MetricSnapshotRepository,
    private val campaignRepository: CampaignRepository,
    private val workspaceRepository: WorkspaceRepository,
) {

    fun getAnalytics(userId: Long, workspaceId: Long, campaignId: Long): CampaignAnalyticsResponse {
        assertWorkspaceAccess(userId, workspaceId)
        loadCampaignInWorkspace(workspaceId, campaignId)

        val posts = campaignPostRepository.findByCampaignId(campaignId)
        var views = 0L
        var likes = 0L
        var comments = 0L
        var shares = 0L
        var lastSyncedAt: LocalDateTime? = null

        val postMetrics = posts.map { post ->
            val postId = post.id!!
            val latest = metricSnapshotRepository.findLatestByCampaignPostId(postId)
            if (latest != null) {
                views += latest.views
                likes += latest.likes
                comments += latest.comments
                shares += latest.shares
                val current = lastSyncedAt
                if (current == null || latest.capturedAt.isAfter(current)) {
                    lastSyncedAt = latest.capturedAt
                }
            }
            PostMetricResponse(
                campaignPostId = postId,
                platform = post.platform,
                postStatus = post.status.name,
                views = latest?.views ?: 0,
                likes = latest?.likes ?: 0,
                comments = latest?.comments ?: 0,
                shares = latest?.shares ?: 0,
                capturedAt = latest?.capturedAt,
            )
        }

        return CampaignAnalyticsResponse(
            campaignId = campaignId,
            totalViews = views,
            totalLikes = likes,
            totalComments = comments,
            totalShares = shares,
            lastSyncedAt = lastSyncedAt,
            posts = postMetrics,
        )
    }

    @Transactional
    fun recordMetric(userId: Long, workspaceId: Long, campaignPostId: Long, request: RecordMetricRequest): PostMetricResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val post = campaignPostRepository.findById(campaignPostId) ?: throw NotFoundException("게시물", campaignPostId)
        loadCampaignInWorkspace(workspaceId, post.campaignId)

        val snapshot = metricSnapshotRepository.save(
            MetricSnapshot(
                campaignPostId = campaignPostId,
                capturedAt = LocalDateTime.now(),
                views = request.views,
                likes = request.likes,
                comments = request.comments,
                shares = request.shares,
            ),
        )
        return PostMetricResponse(
            campaignPostId = campaignPostId,
            platform = post.platform,
            postStatus = post.status.name,
            views = snapshot.views,
            likes = snapshot.likes,
            comments = snapshot.comments,
            shares = snapshot.shares,
            capturedAt = snapshot.capturedAt,
        )
    }

    private fun assertWorkspaceAccess(userId: Long, workspaceId: Long) {
        val accessible = workspaceRepository.findAccessibleByUserId(userId).any { it.id == workspaceId }
        if (!accessible) throw NotFoundException("워크스페이스", workspaceId)
    }

    private fun loadCampaignInWorkspace(workspaceId: Long, campaignId: Long) {
        val campaign = campaignRepository.findById(campaignId) ?: throw NotFoundException("캠페인", campaignId)
        if (campaign.workspaceId != workspaceId) throw NotFoundException("캠페인", campaignId)
    }
}
