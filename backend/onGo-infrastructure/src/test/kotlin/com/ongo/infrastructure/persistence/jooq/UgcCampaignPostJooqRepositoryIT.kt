package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.campaign.Campaign
import com.ongo.domain.ugc.campaign.CampaignRepository
import com.ongo.domain.ugc.campaign.CampaignStatus
import com.ongo.domain.ugc.publishing.CampaignPost
import com.ongo.domain.ugc.publishing.CampaignPostRepository
import com.ongo.domain.ugc.publishing.PostStatus
import com.ongo.domain.ugc.publishing.PostType
import com.ongo.domain.ugc.submission.ContentSubmission
import com.ongo.domain.ugc.submission.SubmissionAsset
import com.ongo.domain.ugc.submission.SubmissionRepository
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_CAMPAIGNS
import org.jooq.DSLContext
import org.jooq.exception.IntegrityConstraintViolationException
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

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class UgcCampaignPostJooqRepositoryIT {
    @Autowired lateinit var campaignRepo: CampaignRepository
    @Autowired lateinit var submissionRepo: SubmissionRepository
    @Autowired lateinit var postRepo: CampaignPostRepository
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
    private var submissionId = 0L

    @BeforeEach
    fun setup() {
        dsl.deleteFrom(UGC_CAMPAIGNS).execute()
        campaignId = campaignRepo.save(
            Campaign(workspaceId = 1, name = "c", status = CampaignStatus.RECRUITING, createdBy = 1),
        ).id!!
        submissionId = submissionRepo.save(
            ContentSubmission(
                campaignId = campaignId, creatorId = 100, caption = "x",
                assets = listOf(SubmissionAsset(assetType = "VIDEO", resourceType = "video", resourceId = 7)),
            ),
        ).id!!
    }

    private fun directPost(key: String) = CampaignPost(
        campaignId = campaignId, submissionId = submissionId, creatorId = 100,
        platform = "YOUTUBE", postType = PostType.DIRECT, videoUploadId = 11,
        status = PostStatus.PUBLISHING, idempotencyKey = key,
    )

    @Test
    fun `direct post save and roundtrip`() {
        val saved = postRepo.save(directPost("sub:1:plat:YOUTUBE"))
        val found = postRepo.findById(saved.id!!)!!
        assertEquals(PostType.DIRECT, found.postType)
        assertEquals(PostStatus.PUBLISHING, found.status)
        assertEquals(11, found.videoUploadId)
    }

    @Test
    fun `idempotency key is unique`() {
        postRepo.save(directPost("dup-key"))
        assertThrows(IntegrityConstraintViolationException::class.java) {
            postRepo.save(directPost("dup-key"))
        }
    }

    @Test
    fun `external post save and roundtrip`() {
        val saved = postRepo.save(
            CampaignPost(
                campaignId = campaignId, submissionId = submissionId, creatorId = 100,
                platform = "TIKTOK", postType = PostType.EXTERNAL,
                externalPostUrl = "https://www.tiktok.com/@a/video/1", platformPostId = "1",
                status = PostStatus.EXTERNAL, idempotencyKey = "ext:1:TIKTOK:1",
            ),
        )
        val found = postRepo.findById(saved.id!!)!!
        assertEquals(PostType.EXTERNAL, found.postType)
        assertEquals("https://www.tiktok.com/@a/video/1", found.externalPostUrl)
        assertEquals("1", found.platformPostId)
    }

    @Test
    fun `updateStatus transitions to published`() {
        val saved = postRepo.save(directPost("k1"))
        postRepo.updateStatus(saved.id!!, PostStatus.PUBLISHED, "yt-video-123", null)
        val found = postRepo.findById(saved.id!!)!!
        assertEquals(PostStatus.PUBLISHED, found.status)
        assertEquals("yt-video-123", found.platformPostId)
    }

    @Test
    fun `find by submission and campaign`() {
        postRepo.save(directPost("k-a"))
        postRepo.save(directPost("k-b").copy(platform = "TIKTOK"))
        assertEquals(2, postRepo.findBySubmissionId(submissionId).size)
        assertEquals(2, postRepo.findByCampaignId(campaignId).size)
    }
}
