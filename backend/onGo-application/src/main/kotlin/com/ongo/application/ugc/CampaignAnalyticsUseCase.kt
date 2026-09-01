package com.ongo.application.ugc

import com.ongo.application.ugc.dto.CampaignAnalyticsResponse
import com.ongo.application.ugc.dto.PostMetricResponse
import com.ongo.application.ugc.dto.RecordMetricRequest
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.analytics.MetricSnapshot
import com.ongo.domain.ugc.analytics.MetricSnapshotRepository
import com.ongo.domain.ugc.analytics.MetricSnapshotSource
import com.ongo.domain.ugc.campaign.CampaignRepository
import com.ongo.domain.ugc.publishing.CampaignPostRepository
import com.ongo.domain.workspace.WorkspaceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * UGC 캠페인 성과 조회. 게시물별 최신 지표 스냅샷을 합산하고 마지막 동기화 시각을 함께 반환한다.
 * 지표 기록(recordMetric)은 수동 보정·백필을 위한 진입점이며,
 * 외부 게시물의 최신 지표는 CampaignMetricsSyncScheduler가 플랫폼 API에서 동기화한다.
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

        /*
         * **측정하지 않은 값을 합산에 섞지 않는다.**
         *
         * 예전에는 스냅샷의 네 숫자를 플랫폼 구분 없이 더했다. Facebook·WordPress·Vimeo 는
         * 공유 수를, Pinterest 는 댓글 수를 API 로 주지 않는데 그 자리의 0 이 그대로
         * 더해졌다. 그 합계가 브랜드 성과와 **보상 판단** 화면에 올라간다 — 실제로 공유가
         * 없어서가 아니라 물어보지 않았기 때문인데 화면은 둘을 구분하지 못했다.
         *
         * 판정은 스냅샷 자신이 한다([MetricSnapshot.measured]). 플랫폼 이름으로 거르면
         * 운영자가 손으로 넣은 백필까지 함께 버리게 된다.
         */
        val snapshotByPost = posts.associate { it.id!! to metricSnapshotRepository.findLatestByCampaignPostId(it.id!!) }

        val measuredValues = MetricSnapshot.ALL_METRICS.associateWith { metric ->
            snapshotByPost.values.mapNotNull { it?.measuredValue(metric) }
        }
        // 측정한 게시물이 하나도 없으면 합계는 null 이다. 0 은 "공유 0회" 라는 주장이 된다.
        val totals = measuredValues.mapValues { (_, values) -> values.takeIf { it.isNotEmpty() }?.sum() }
        val measuredCounts = measuredValues.mapValues { (_, values) -> values.size }

        val lastSyncedAt = snapshotByPost.values.filterNotNull().maxByOrNull { it.capturedAt }?.capturedAt

        val postMetrics = posts.map { post ->
            val postId = post.id!!
            val latest = snapshotByPost[postId]
            PostMetricResponse(
                campaignPostId = postId,
                platform = post.platform,
                postStatus = post.status.name,
                views = latest?.measuredValue(MetricSnapshot.VIEWS),
                likes = latest?.measuredValue(MetricSnapshot.LIKES),
                comments = latest?.measuredValue(MetricSnapshot.COMMENTS),
                shares = latest?.measuredValue(MetricSnapshot.SHARES),
                capturedAt = latest?.capturedAt,
                // 스냅샷이 아예 없으면 네 지표 모두 측정 불가다.
                unavailableMetrics = MetricSnapshot.ALL_METRICS.filter { latest?.measured(it) != true },
            )
        }

        return CampaignAnalyticsResponse(
            campaignId = campaignId,
            totalViews = totals[MetricSnapshot.VIEWS],
            totalLikes = totals[MetricSnapshot.LIKES],
            totalComments = totals[MetricSnapshot.COMMENTS],
            totalShares = totals[MetricSnapshot.SHARES],
            lastSyncedAt = lastSyncedAt,
            posts = postMetrics,
            measuredPostCounts = measuredCounts,
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
                /*
                 * **사람이 적은 값이다. 0 도 측정값이다.**
                 *
                 * 플랫폼이 그 지표를 안 준다는 사실과 무관하게, 운영자가 Facebook 게시물의
                 * 공유 수를 손으로 확인해 넣었다면 그것은 관측이다. 그래서 가용성을
                 * 플랫폼이 아니라 스냅샷에 붙인다 — 플랫폼으로 거르면 이 백필이 사라진다.
                 */
                source = MetricSnapshotSource.MANUAL,
                unavailableMetrics = emptySet(),
            ),
        )
        return PostMetricResponse(
            campaignPostId = campaignPostId,
            platform = post.platform,
            postStatus = post.status.name,
            views = snapshot.measuredValue(MetricSnapshot.VIEWS),
            likes = snapshot.measuredValue(MetricSnapshot.LIKES),
            comments = snapshot.measuredValue(MetricSnapshot.COMMENTS),
            shares = snapshot.measuredValue(MetricSnapshot.SHARES),
            capturedAt = snapshot.capturedAt,
            unavailableMetrics = MetricSnapshot.ALL_METRICS.filterNot { snapshot.measured(it) },
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
