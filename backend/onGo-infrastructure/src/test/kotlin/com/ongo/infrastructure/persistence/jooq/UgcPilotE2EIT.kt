package com.ongo.infrastructure.persistence.jooq

import com.ongo.application.ugc.AuditRecorder
import com.ongo.application.ugc.CampaignAnalyticsUseCase
import com.ongo.application.ugc.CampaignPublishingUseCase
import com.ongo.application.ugc.CampaignUseCase
import com.ongo.application.ugc.InviteTokenService
import com.ongo.application.ugc.ParticipationUseCase
import com.ongo.application.ugc.RewardUseCase
import com.ongo.application.ugc.SubmissionUseCase
import com.ongo.application.ugc.dto.ApplyRequest
import com.ongo.application.ugc.dto.CreateCampaignRequest
import com.ongo.application.ugc.dto.CreateInviteRequest
import com.ongo.application.ugc.dto.CreateSubmissionRequest
import com.ongo.application.ugc.dto.RecordMetricRequest
import com.ongo.application.ugc.dto.RegisterExternalPostRequest
import com.ongo.application.ugc.dto.ReviewDecisionRequest
import com.ongo.application.ugc.dto.SubmissionAssetDto
import com.ongo.application.ugc.dto.UpdateRewardRequest
import com.ongo.application.ugc.dto.UpsertPlaybookRequest
import com.ongo.domain.ugc.campaign.CampaignRepository
import com.ongo.domain.ugc.campaign.PlaybookRepository
import com.ongo.domain.ugc.participation.ApplicationRepository
import com.ongo.domain.ugc.participation.InviteRepository
import com.ongo.domain.ugc.participation.ParticipantRepository
import com.ongo.domain.ugc.publishing.CampaignPostRepository
import com.ongo.domain.ugc.publishing.CampaignPublishPort
import com.ongo.domain.ugc.publishing.PlatformPublishOutcome
import com.ongo.domain.ugc.analytics.MetricSnapshotRepository
import com.ongo.domain.ugc.audit.AuditEventRepository
import com.ongo.domain.ugc.reward.RewardRepository
import com.ongo.domain.ugc.submission.SubmissionRepository
import com.ongo.domain.ugc.submission.SubmissionReviewRepository
import com.ongo.domain.workspace.Workspace
import com.ongo.domain.workspace.WorkspaceRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_CAMPAIGNS
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

/**
 * UGC 유료 파일럿 전체 happy-path E2E (서비스 레이어 + 실제 Postgres).
 *
 * 브랜드 캠페인 생성 → 공개 → 초대 → (크리에이터) 지원 → 수락 → 제출 → 승인
 * → 외부 게시물 등록 → 지표 기록 → 성과 집계 → 보상 확정 → 지급 CSV 까지 관통한다.
 *
 * SNS 직접 게시는 채널/영상 의존이 있어 외부 게시물 등록 경로로 검증한다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class UgcPilotE2EIT {
    @Autowired lateinit var campaignRepo: CampaignRepository
    @Autowired lateinit var playbookRepo: PlaybookRepository
    @Autowired lateinit var applicationRepo: ApplicationRepository
    @Autowired lateinit var participantRepo: ParticipantRepository
    @Autowired lateinit var inviteRepo: InviteRepository
    @Autowired lateinit var submissionRepo: SubmissionRepository
    @Autowired lateinit var reviewRepo: SubmissionReviewRepository
    @Autowired lateinit var postRepo: CampaignPostRepository
    @Autowired lateinit var metricRepo: MetricSnapshotRepository
    @Autowired lateinit var rewardRepo: RewardRepository
    @Autowired lateinit var auditEventRepo: AuditEventRepository
    @Autowired lateinit var workspaceRepo: WorkspaceRepository
    @Autowired lateinit var videoRepo: VideoRepository
    @Autowired lateinit var dsl: DSLContext

    companion object {
        private const val BRAND = 90001L
        private const val CREATOR = 90002L

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

    // 게시는 외부등록 경로만 사용하므로 포트는 호출되지 않는다(no-op).
    private val noopPublishPort = object : CampaignPublishPort {
        override fun publish(creatorId: Long, videoId: Long, platforms: List<String>): List<PlatformPublishOutcome> = emptyList()
    }
    private val inviteTokenService = InviteTokenService()

    private lateinit var campaignUseCase: CampaignUseCase
    private lateinit var participationUseCase: ParticipationUseCase
    private lateinit var submissionUseCase: SubmissionUseCase
    private lateinit var publishingUseCase: CampaignPublishingUseCase
    private lateinit var analyticsUseCase: CampaignAnalyticsUseCase
    private lateinit var rewardUseCase: RewardUseCase

    private var workspaceId = 0L

    @BeforeEach
    fun setup() {
        dsl.deleteFrom(UGC_CAMPAIGNS).execute()
        dsl.execute("DELETE FROM workspaces WHERE owner_id = ?", BRAND)
        dsl.execute("DELETE FROM users WHERE id = ?", BRAND)
        dsl.execute(
            "INSERT INTO users (id, email, name, provider, provider_id, plan_type) " +
                "VALUES (?, 'brand@test.com', 'Brand', 'GOOGLE', 'g-brand', 'FREE')",
            BRAND,
        )
        workspaceId = workspaceRepo.save(Workspace(ownerId = BRAND, name = "Brand WS", slug = "brand-ws")).id!!

        campaignUseCase = CampaignUseCase(campaignRepo, playbookRepo, workspaceRepo)
        participationUseCase = ParticipationUseCase(applicationRepo, participantRepo, inviteRepo, campaignRepo, playbookRepo, workspaceRepo, inviteTokenService)
        submissionUseCase = SubmissionUseCase(submissionRepo, reviewRepo, participantRepo, campaignRepo, workspaceRepo, videoRepo)
        publishingUseCase = CampaignPublishingUseCase(postRepo, submissionRepo, campaignRepo, workspaceRepo, noopPublishPort)
        analyticsUseCase = CampaignAnalyticsUseCase(postRepo, metricRepo, campaignRepo, workspaceRepo)
        rewardUseCase = RewardUseCase(rewardRepo, participantRepo, campaignRepo, workspaceRepo, AuditRecorder(auditEventRepo))
    }

    @Test
    fun `full pilot happy path from campaign creation to payout csv`() {
        // 1. 캠페인 생성 (DRAFT)
        val created = campaignUseCase.createCampaign(
            BRAND, workspaceId,
            CreateCampaignRequest(
                name = "여름 UGC", totalBudget = 1_000_000, fixedRewardPerCreator = 100_000,
                startAt = LocalDateTime.of(2026, 8, 1, 0, 0), endAt = LocalDateTime.of(2026, 8, 31, 0, 0),
            ),
        )
        val campaignId = created.campaign.id
        assertEquals("DRAFT", created.campaign.status)

        // 2. 플레이북 + 3. 공개
        campaignUseCase.upsertPlaybook(BRAND, workspaceId, campaignId, UpsertPlaybookRequest(title = "제작 가이드"))
        val published = campaignUseCase.publishCampaign(BRAND, workspaceId, campaignId)
        assertEquals("RECRUITING", published.campaign.status)

        // 4. 초대 → 5. 지원
        val invite = participationUseCase.createInvite(BRAND, workspaceId, campaignId, CreateInviteRequest(maxUses = 10))
        val token = invite.token!!
        participationUseCase.apply(CREATOR, token, ApplyRequest(message = "지원합니다"))

        // 6. 지원자 목록 → 7. 수락
        val applications = participationUseCase.listApplications(BRAND, workspaceId, campaignId, null, 0, 20)
        val applicationId = applications.items.first().id
        val accepted = participationUseCase.acceptApplication(BRAND, workspaceId, applicationId)
        assertEquals("ACCEPTED", accepted.status)

        // 8. 제출 → 9. 제출하기 → 10. 승인
        val submission = submissionUseCase.createOrUpdateSubmission(
            CREATOR, campaignId,
            CreateSubmissionRequest(caption = "완성했어요", assets = listOf(SubmissionAssetDto(assetType = "EXTERNAL", externalUrl = "https://drive.example/v"))),
        )
        val submissionId = submission.id
        submissionUseCase.submitSubmission(CREATOR, submissionId)
        val approved = submissionUseCase.approveSubmission(BRAND, workspaceId, submissionId, ReviewDecisionRequest(comment = "좋아요"))
        assertEquals("APPROVED", approved.status)

        // 11. 외부 게시물 등록
        val post = publishingUseCase.registerExternalPost(
            CREATOR, submissionId,
            RegisterExternalPostRequest(platform = "YOUTUBE", externalPostUrl = "https://www.youtube.com/watch?v=abc", platformPostId = "abc"),
        )
        assertEquals("EXTERNAL", post.postType)

        // 12. 지표 기록 → 13. 성과 집계
        analyticsUseCase.recordMetric(BRAND, workspaceId, post.id, RecordMetricRequest(views = 1_000, likes = 100, comments = 10, shares = 5))
        val analytics = analyticsUseCase.getAnalytics(BRAND, workspaceId, campaignId)
        assertEquals(1_000, analytics.totalViews)
        assertEquals(1, analytics.posts.size)

        // 14. 참여자 보상 → 15. 금액 입력 → 16. 확정
        val participantId = rewardUseCase.listParticipantRewards(BRAND, workspaceId, campaignId).items.first().participantId
        rewardUseCase.updateReward(BRAND, workspaceId, participantId, UpdateRewardRequest(baseAmount = 100_000))
        val confirmed = rewardUseCase.confirmReward(BRAND, workspaceId, participantId)
        assertEquals("CONFIRMED", confirmed.status)

        // 감사 로그: 확정이 기록되었는지 확인
        val auditEvents = auditEventRepo.findByCampaignId(campaignId, 0, 10)
        assertTrue(auditEvents.any { it.action == "REWARD_CONFIRMED" }, "reward confirmation must be audited")

        // 17. 지급 CSV
        val csv = rewardUseCase.exportRewardsCsv(BRAND, workspaceId, campaignId).toString(Charsets.UTF_8)
        assertTrue(csv.startsWith("﻿"), "CSV must start with UTF-8 BOM")
        assertTrue(csv.contains(CREATOR.toString()), "CSV must contain the creator")
        assertTrue(csv.contains("100000"), "CSV must contain the confirmed amount")
    }
}
