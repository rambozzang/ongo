package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.campaign.Campaign
import com.ongo.domain.ugc.campaign.CampaignRepository
import com.ongo.domain.ugc.campaign.CampaignStatus
import com.ongo.domain.ugc.campaign.Playbook
import com.ongo.domain.ugc.campaign.PlaybookRepository
import com.ongo.domain.ugc.campaign.PlaybookStep
import com.ongo.domain.ugc.participation.ApplicationRepository
import com.ongo.domain.ugc.participation.CampaignApplication
import com.ongo.domain.ugc.participation.CampaignInvite
import com.ongo.domain.ugc.participation.CampaignParticipant
import com.ongo.domain.ugc.participation.InviteRepository
import com.ongo.domain.ugc.participation.ParticipantRepository
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
class UgcCampaignJooqRepositoryIT {
    @Autowired lateinit var campaignRepo: CampaignRepository
    @Autowired lateinit var playbookRepo: PlaybookRepository
    @Autowired lateinit var applicationRepo: ApplicationRepository
    @Autowired lateinit var participantRepo: ParticipantRepository
    @Autowired lateinit var inviteRepo: InviteRepository
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

    private val workspaceId = 1L
    private val createdBy = 1L

    @BeforeEach
    fun setup() {
        // FK ON DELETE CASCADE 로 자식(playbook/step/application/participant/invite)까지 정리
        dsl.deleteFrom(UGC_CAMPAIGNS).execute()
    }

    private fun newCampaign(status: CampaignStatus = CampaignStatus.DRAFT) = Campaign(
        workspaceId = workspaceId,
        name = "여름 캠페인",
        status = status,
        totalBudget = 1_000_000,
        fixedRewardPerCreator = 100_000,
        startAt = LocalDateTime.of(2026, 8, 1, 0, 0),
        endAt = LocalDateTime.of(2026, 8, 31, 0, 0),
        createdBy = createdBy,
    )

    @Test
    fun `campaign save and findById roundtrip`() {
        val saved = campaignRepo.save(newCampaign())
        assertNotNull(saved.id)
        val found = campaignRepo.findById(saved.id!!)!!
        assertEquals("여름 캠페인", found.name)
        assertEquals(CampaignStatus.DRAFT, found.status)
        assertEquals(1_000_000, found.totalBudget)
        assertEquals(0, found.version)
    }

    @Test
    fun `campaign optimistic lock rejects stale update`() {
        val saved = campaignRepo.save(newCampaign())
        val updated = campaignRepo.update(saved.copy(name = "수정됨"))
        assertEquals(1, updated.version)
        // 원래 version(0)으로 다시 갱신 시도 → 충돌
        assertThrows(IllegalStateException::class.java) {
            campaignRepo.update(saved.copy(name = "다시"))
        }
    }

    @Test
    fun `playbook upsert replaces steps and bumps revision`() {
        val campaign = campaignRepo.save(newCampaign())
        val cid = campaign.id!!

        playbookRepo.upsert(
            Playbook(
                campaignId = cid, title = "플레이북", contentType = "UGC_VIDEO",
                steps = listOf(
                    PlaybookStep(sortOrder = 0, title = "단계1"),
                    PlaybookStep(sortOrder = 1, title = "단계2"),
                ),
            ),
        )
        val first = playbookRepo.findByCampaignId(cid)!!
        assertEquals(2, first.steps.size)
        assertEquals(1, first.revision)

        val second = playbookRepo.upsert(
            Playbook(campaignId = cid, title = "플레이북 v2", steps = listOf(PlaybookStep(sortOrder = 0, title = "단계만"))),
        )
        assertEquals(1, second.steps.size)
        assertEquals(2, second.revision)
    }

    @Test
    fun `application unique blocks duplicate application`() {
        val cid = campaignRepo.save(newCampaign(CampaignStatus.RECRUITING)).id!!
        applicationRepo.save(CampaignApplication(campaignId = cid, creatorId = 100))
        assertThrows(IntegrityConstraintViolationException::class.java) {
            applicationRepo.save(CampaignApplication(campaignId = cid, creatorId = 100))
        }
    }

    @Test
    fun `participant unique blocks duplicate participant (concurrent accept guard)`() {
        val cid = campaignRepo.save(newCampaign(CampaignStatus.RECRUITING)).id!!
        participantRepo.save(CampaignParticipant(campaignId = cid, creatorId = 100, agreedReward = 100_000))
        assertThrows(IntegrityConstraintViolationException::class.java) {
            participantRepo.save(CampaignParticipant(campaignId = cid, creatorId = 100, agreedReward = 100_000))
        }
    }

    @Test
    fun `invite token hash is unique and used count increments`() {
        val cid = campaignRepo.save(newCampaign(CampaignStatus.RECRUITING)).id!!
        val invite = inviteRepo.save(
            CampaignInvite(campaignId = cid, tokenHash = "hash-1", maxUses = 5, createdBy = createdBy),
        )
        assertThrows(IntegrityConstraintViolationException::class.java) {
            inviteRepo.save(CampaignInvite(campaignId = cid, tokenHash = "hash-1", createdBy = createdBy))
        }

        inviteRepo.incrementUsedCount(invite.id!!)
        assertEquals(1, inviteRepo.findByTokenHash("hash-1")!!.usedCount)
    }
}
