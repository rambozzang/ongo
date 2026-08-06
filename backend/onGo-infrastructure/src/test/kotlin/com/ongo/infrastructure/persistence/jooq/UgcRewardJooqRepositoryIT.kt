package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.analytics.MetricSnapshot
import com.ongo.domain.ugc.analytics.MetricSnapshotRepository
import com.ongo.domain.ugc.campaign.Campaign
import com.ongo.domain.ugc.campaign.CampaignRepository
import com.ongo.domain.ugc.campaign.CampaignStatus
import com.ongo.domain.ugc.participation.CampaignParticipant
import com.ongo.domain.ugc.participation.ParticipantRepository
import com.ongo.domain.ugc.publishing.CampaignPost
import com.ongo.domain.ugc.publishing.CampaignPostRepository
import com.ongo.domain.ugc.publishing.PostStatus
import com.ongo.domain.ugc.publishing.PostType
import com.ongo.domain.ugc.reward.RewardConfirmation
import com.ongo.domain.ugc.reward.RewardRepository
import com.ongo.domain.ugc.reward.RewardStatus
import com.ongo.domain.ugc.submission.ContentSubmission
import com.ongo.domain.ugc.submission.SubmissionAsset
import com.ongo.domain.ugc.submission.SubmissionRepository
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_CAMPAIGNS
import org.jooq.DSLContext
import org.springframework.dao.DuplicateKeyException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class UgcRewardJooqRepositoryIT {
    @Autowired lateinit var campaignRepo: CampaignRepository
    @Autowired lateinit var participantRepo: ParticipantRepository
    @Autowired lateinit var submissionRepo: SubmissionRepository
    @Autowired lateinit var postRepo: CampaignPostRepository
    @Autowired lateinit var rewardRepo: RewardRepository
    @Autowired lateinit var metricRepo: MetricSnapshotRepository
    @Autowired lateinit var dsl: DSLContext

    companion object {
        @Container @JvmStatic
        val pg = PostgreSQLContainer("postgres:16").apply {
            withDatabaseName("ongo_test"); withUsername("test"); withPassword("test")
        }

        @JvmStatic @DynamicPropertySource
        fun props(r: DynamicPropertyRegistry) {
            r.add("spring.datasource.url") { pg.jdbcUrl }
            r.add("spring.datasource.username") { pg.username }
            r.add("spring.datasource.password") { pg.password }
        }
    }

    private var campaignId = 0L

    @BeforeEach
    fun setup() {
        dsl.deleteFrom(UGC_CAMPAIGNS).execute()
        campaignId = campaignRepo.save(
            Campaign(workspaceId = 1, name = "c", status = CampaignStatus.RECRUITING, totalBudget = 1_000_000, createdBy = 1),
        ).id!!
    }

    private fun participant(creatorId: Long): Long =
        participantRepo.save(CampaignParticipant(campaignId = campaignId, creatorId = creatorId, agreedReward = 100_000)).id!!

    @Test
    fun `reward save roundtrip and unique per participant`() {
        val pid = participant(100)
        val saved = rewardRepo.save(
            RewardConfirmation(participantId = pid, campaignId = campaignId, creatorId = 100, baseAmount = 80_000, totalAmount = 80_000),
        )
        val found = rewardRepo.findByParticipantId(pid)!!
        assertEquals(80_000, found.totalAmount)
        assertEquals(RewardStatus.DRAFT, found.status)
        assertEquals(saved.id, found.id)

        assertThrows(DuplicateKeyException::class.java) {
            rewardRepo.save(RewardConfirmation(participantId = pid, campaignId = campaignId, creatorId = 100))
        }
    }

    @Test
    fun `sumSettledTotalByCampaign counts only confirmed and paid`() {
        val p1 = participant(100)
        val p2 = participant(200)
        rewardRepo.save(RewardConfirmation(participantId = p1, campaignId = campaignId, creatorId = 100, totalAmount = 50_000, status = RewardStatus.DRAFT))
        rewardRepo.save(RewardConfirmation(participantId = p2, campaignId = campaignId, creatorId = 200, totalAmount = 30_000, status = RewardStatus.CONFIRMED))

        assertEquals(30_000, rewardRepo.sumSettledTotalByCampaign(campaignId))
    }

    @Test
    fun `metric snapshot latest and unique per captured_at`() {
        val submissionId = submissionRepo.save(
            ContentSubmission(campaignId = campaignId, creatorId = 100, assets = listOf(SubmissionAsset(assetType = "EXTERNAL", externalUrl = "https://a/b"))),
        ).id!!
        val postId = postRepo.save(
            CampaignPost(campaignId = campaignId, submissionId = submissionId, creatorId = 100, platform = "YOUTUBE", postType = PostType.DIRECT, status = PostStatus.PUBLISHING, idempotencyKey = "k1"),
        ).id!!

        metricRepo.save(MetricSnapshot(campaignPostId = postId, capturedAt = LocalDateTime.of(2026, 8, 10, 0, 0), views = 100))
        metricRepo.save(MetricSnapshot(campaignPostId = postId, capturedAt = LocalDateTime.of(2026, 8, 11, 0, 0), views = 250))

        val latest = metricRepo.findLatestByCampaignPostId(postId)!!
        assertEquals(250, latest.views)

        assertThrows(DuplicateKeyException::class.java) {
            metricRepo.save(MetricSnapshot(campaignPostId = postId, capturedAt = LocalDateTime.of(2026, 8, 11, 0, 0), views = 999))
        }
    }
}
