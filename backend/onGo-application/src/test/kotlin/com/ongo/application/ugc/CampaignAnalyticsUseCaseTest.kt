package com.ongo.application.ugc

import com.ongo.application.ugc.dto.RecordMetricRequest
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.analytics.MetricSnapshot
import com.ongo.domain.ugc.analytics.MetricSnapshotRepository
import com.ongo.domain.ugc.campaign.Campaign
import com.ongo.domain.ugc.campaign.CampaignRepository
import com.ongo.domain.ugc.publishing.CampaignPost
import com.ongo.domain.ugc.publishing.CampaignPostRepository
import com.ongo.domain.ugc.publishing.PostStatus
import com.ongo.domain.ugc.publishing.PostType
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
class CampaignAnalyticsUseCaseTest {

    @MockK lateinit var campaignPostRepository: CampaignPostRepository
    @MockK lateinit var metricSnapshotRepository: MetricSnapshotRepository
    @MockK lateinit var campaignRepository: CampaignRepository
    @MockK lateinit var workspaceRepository: WorkspaceRepository

    @InjectMockKs lateinit var useCase: CampaignAnalyticsUseCase

    private val userId = 1L
    private val workspaceId = 10L
    private val campaignId = 50L

    private fun grantAccess(vararg ids: Long) {
        every { workspaceRepository.findAccessibleByUserId(userId) } returns
            ids.map { Workspace(id = it, ownerId = userId, name = "w$it", slug = "w$it") }
    }

    private fun campaign() = Campaign(id = campaignId, workspaceId = workspaceId, name = "c", createdBy = 1)

    private fun post(id: Long, platform: String) = CampaignPost(
        id = id, campaignId = campaignId, submissionId = 1, creatorId = 100,
        platform = platform, postType = PostType.DIRECT, status = PostStatus.PUBLISHING, idempotencyKey = "k$id",
    )

    @Test
    fun `analytics aggregates the latest snapshot per post`() {
        grantAccess(workspaceId)
        every { campaignRepository.findById(campaignId) } returns campaign()
        every { campaignPostRepository.findByCampaignId(campaignId) } returns listOf(post(1, "YOUTUBE"), post(2, "TIKTOK"))
        every { metricSnapshotRepository.findLatestByCampaignPostId(1) } returns
            MetricSnapshot(campaignPostId = 1, capturedAt = LocalDateTime.of(2026, 8, 10, 0, 0), views = 100, likes = 10)
        every { metricSnapshotRepository.findLatestByCampaignPostId(2) } returns
            MetricSnapshot(campaignPostId = 2, capturedAt = LocalDateTime.of(2026, 8, 11, 0, 0), views = 50, likes = 5)

        val result = useCase.getAnalytics(userId, workspaceId, campaignId)

        assertEquals(150, result.totalViews)
        assertEquals(15, result.totalLikes)
        assertEquals(2, result.posts.size)
        assertEquals(LocalDateTime.of(2026, 8, 11, 0, 0), result.lastSyncedAt)
    }

    @Test
    fun `record metric saves a snapshot for a post in the workspace`() {
        grantAccess(workspaceId)
        every { campaignPostRepository.findById(1) } returns post(1, "YOUTUBE")
        every { campaignRepository.findById(campaignId) } returns campaign()
        val saved = slot<MetricSnapshot>()
        every { metricSnapshotRepository.save(capture(saved)) } answers { saved.captured.copy(id = 1) }

        val result = useCase.recordMetric(userId, workspaceId, 1, RecordMetricRequest(views = 200, likes = 20))

        assertEquals(200, saved.captured.views)
        assertEquals(200, result.views)
    }

    @Test
    fun `analytics is blocked with 404 without workspace access`() {
        grantAccess()
        assertFailsWith<NotFoundException> { useCase.getAnalytics(userId, workspaceId, campaignId) }
    }
}
