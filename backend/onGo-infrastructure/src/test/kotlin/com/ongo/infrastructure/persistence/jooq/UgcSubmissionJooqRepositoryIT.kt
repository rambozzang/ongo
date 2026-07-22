package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.campaign.Campaign
import com.ongo.domain.ugc.campaign.CampaignRepository
import com.ongo.domain.ugc.campaign.CampaignStatus
import com.ongo.domain.ugc.submission.ContentSubmission
import com.ongo.domain.ugc.submission.SubmissionAsset
import com.ongo.domain.ugc.submission.SubmissionRepository
import com.ongo.domain.ugc.submission.SubmissionReview
import com.ongo.domain.ugc.submission.SubmissionReviewRepository
import com.ongo.domain.ugc.submission.SubmissionStatus
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_CAMPAIGNS
import org.jooq.DSLContext
import org.jooq.exception.IntegrityConstraintViolationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
class UgcSubmissionJooqRepositoryIT {
    @Autowired lateinit var campaignRepo: CampaignRepository
    @Autowired lateinit var submissionRepo: SubmissionRepository
    @Autowired lateinit var reviewRepo: SubmissionReviewRepository
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
            Campaign(workspaceId = 1, name = "캠페인", status = CampaignStatus.RECRUITING, createdBy = 1),
        ).id!!
    }

    private fun submission(creatorId: Long = 100, assets: List<SubmissionAsset> = listOf(SubmissionAsset(assetType = "EXTERNAL", externalUrl = "https://a/b"))) =
        ContentSubmission(campaignId = campaignId, creatorId = creatorId, caption = "cap", assets = assets)

    @Test
    fun `submission save with assets roundtrip`() {
        val saved = submissionRepo.save(
            submission(assets = listOf(
                SubmissionAsset(assetType = "VIDEO", resourceType = "video", resourceId = 7),
                SubmissionAsset(assetType = "EXTERNAL", externalUrl = "https://x/y"),
            )),
        )
        val found = submissionRepo.findById(saved.id!!)!!
        assertEquals(2, found.assets.size)
        assertEquals(SubmissionStatus.DRAFT, found.status)
        assertEquals("video", found.assets[0].resourceType)
    }

    @Test
    fun `unique blocks a second submission by the same creator`() {
        submissionRepo.save(submission(creatorId = 100))
        assertThrows(IntegrityConstraintViolationException::class.java) {
            submissionRepo.save(submission(creatorId = 100))
        }
    }

    @Test
    fun `update replaces assets and bumps revision`() {
        val saved = submissionRepo.save(submission(assets = listOf(
            SubmissionAsset(assetType = "EXTERNAL", externalUrl = "https://one"),
            SubmissionAsset(assetType = "EXTERNAL", externalUrl = "https://two"),
        )))
        val updated = submissionRepo.update(
            saved.copy(caption = "고침", revision = 2, assets = listOf(SubmissionAsset(assetType = "EXTERNAL", externalUrl = "https://only"))),
        )
        assertEquals(1, updated.assets.size)
        assertEquals(2, updated.revision)
        assertEquals("고침", submissionRepo.findById(saved.id!!)!!.caption)
    }

    @Test
    fun `updateStatus persists submitted and approved timestamps`() {
        val saved = submissionRepo.save(submission())
        val submitted = submissionRepo.updateStatus(saved.copy(status = SubmissionStatus.SUBMITTED, submittedAt = LocalDateTime.now()))
        assertEquals(SubmissionStatus.SUBMITTED, submitted.status)
        assertNotNull(submitted.submittedAt)
    }

    @Test
    fun `review saved and listed by submission`() {
        val saved = submissionRepo.save(submission())
        reviewRepo.save(SubmissionReview(submissionId = saved.id!!, reviewerId = 1, decision = "CHANGES_REQUESTED", comment = "사유"))
        val reviews = reviewRepo.findBySubmissionId(saved.id!!)
        assertEquals(1, reviews.size)
        assertEquals("CHANGES_REQUESTED", reviews[0].decision)
        assertEquals("사유", reviews[0].comment)
    }
}
