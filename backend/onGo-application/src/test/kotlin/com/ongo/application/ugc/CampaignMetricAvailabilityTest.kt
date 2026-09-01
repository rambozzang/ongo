package com.ongo.application.ugc

import com.ongo.application.analytics.PlatformMetricAvailability
import com.ongo.application.ugc.dto.RecordMetricRequest
import com.ongo.domain.ugc.analytics.MetricSnapshot
import com.ongo.domain.ugc.analytics.MetricSnapshotRepository
import com.ongo.domain.ugc.analytics.MetricSnapshotSource
import com.ongo.domain.ugc.campaign.Campaign
import com.ongo.domain.ugc.campaign.CampaignRepository
import com.ongo.domain.ugc.publishing.CampaignPost
import com.ongo.domain.ugc.publishing.CampaignPostRepository
import com.ongo.domain.ugc.publishing.PostStatus
import com.ongo.domain.ugc.publishing.PostType
import com.ongo.domain.workspace.Workspace
import com.ongo.domain.workspace.WorkspaceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * UGC 캠페인 지표가 **측정하지 않은 값을 성과로 보고하지 않는지** 고정한다.
 *
 * ## 무엇이 보상 판단을 왜곡했나
 *
 * Facebook·WordPress·Vimeo 는 공유 수를, Pinterest 는 댓글 수를 API 로 주지 않는다.
 * 동기화 스케줄러는 클라이언트가 채운 0 을 그대로 저장했고, 캠페인 분석은 플랫폼 구분
 * 없이 합산했다. 그 합계가 `CampaignRewardsView` — **보상 화면** — 에 그대로 올라간다.
 *
 * Facebook 중심 캠페인은 공유 성과가 구조적으로 0 으로 보고됐다. 실제로 공유가 없어서가
 * 아니라 물어보지 않았기 때문인데, 화면은 둘을 구분하지 못했다.
 *
 * ## 왜 플랫폼 이름으로 거르지 않는가
 *
 * 운영자가 손으로 넣는 백필이 있다. 플랫폼만 보고 값을 버리면 **Facebook 게시물의 수동
 * 입력 공유 수까지 함께 사라진다.** 가용성은 플랫폼이 아니라 **그 스냅샷이 어떻게
 * 만들어졌는가**의 문제이므로 스냅샷에 붙인다.
 */
class CampaignMetricAvailabilityTest {

    private val campaignPostRepository = mockk<CampaignPostRepository>()
    private val metricSnapshotRepository = mockk<MetricSnapshotRepository>()
    private val campaignRepository = mockk<CampaignRepository>()
    private val workspaceRepository = mockk<WorkspaceRepository>()

    private val useCase = CampaignAnalyticsUseCase(
        campaignPostRepository = campaignPostRepository,
        metricSnapshotRepository = metricSnapshotRepository,
        campaignRepository = campaignRepository,
        workspaceRepository = workspaceRepository,
    )

    private val userId = 1L
    private val workspaceId = 10L
    private val campaignId = 50L

    private fun grantAccess() {
        every { workspaceRepository.findAccessibleByUserId(userId) } returns
            listOf(Workspace(id = workspaceId, ownerId = userId, name = "w", slug = "w"))
        every { campaignRepository.findById(campaignId) } returns
            Campaign(id = campaignId, workspaceId = workspaceId, name = "c", createdBy = 1)
    }

    private fun post(id: Long, platform: String) = CampaignPost(
        id = id, campaignId = campaignId, submissionId = 1, creatorId = 100,
        platform = platform, postType = PostType.DIRECT, status = PostStatus.PUBLISHING, idempotencyKey = "k$id",
    )

    /** 동기화가 저장하는 것과 같은 모양. 플랫폼이 안 주는 지표를 미측정으로 표시한다. */
    private fun syncSnapshot(postId: Long, platform: String, views: Long, likes: Long, comments: Long, shares: Long) =
        MetricSnapshot(
            campaignPostId = postId,
            capturedAt = LocalDateTime.of(2026, 8, 10, 0, 0),
            views = views, likes = likes, comments = comments, shares = shares,
            source = MetricSnapshotSource.PLATFORM_SYNC,
            unavailableMetrics = MetricSnapshot.ALL_METRICS
                .filterNot { PlatformMetricAvailability.isAvailable(platform, it) }
                .toSet(),
        )

    // ── 미수집을 합산에 섞지 않는다 ──────────────────────────────────────────

    /**
     * **이 케이스가 "공유 0회" 를 만들던 자리다.** Facebook 은 공유 수를 주지 않는다.
     */
    @Test
    @DisplayName("플랫폼이 주지 않는 지표는 합계에서 제외한다")
    fun unavailableMetricIsExcludedFromTheTotal() {
        grantAccess()
        every { campaignPostRepository.findByCampaignId(campaignId) } returns
            listOf(post(1, "YOUTUBE"), post(2, "FACEBOOK"))
        every { metricSnapshotRepository.findLatestByCampaignPostId(1) } returns
            syncSnapshot(1, "YOUTUBE", views = 100, likes = 10, comments = 5, shares = 3)
        every { metricSnapshotRepository.findLatestByCampaignPostId(2) } returns
            syncSnapshot(2, "FACEBOOK", views = 200, likes = 20, comments = 8, shares = 0)

        val result = useCase.getAnalytics(userId, workspaceId, campaignId)

        // 조회수·좋아요·댓글은 양쪽 다 측정된다.
        assertEquals(300, result.totalViews)
        assertEquals(30, result.totalLikes)
        assertEquals(13, result.totalComments)
        // 공유는 YouTube 한 건만 측정됐다. Facebook 의 0 은 미수집이라 더하지 않는다.
        assertEquals(3, result.totalShares)
        assertEquals(1, result.measuredPostCounts[MetricSnapshot.SHARES], "표본 크기가 틀렸다")
        assertEquals(2, result.measuredPostCounts[MetricSnapshot.VIEWS])
    }

    /** 아무도 그 지표를 측정하지 않았으면 합계는 0 이 아니라 **없다.** */
    @Test
    @DisplayName("측정한 게시물이 하나도 없으면 합계는 null이다")
    fun totalIsNullWhenNothingMeasuredThatMetric() {
        grantAccess()
        every { campaignPostRepository.findByCampaignId(campaignId) } returns
            listOf(post(1, "FACEBOOK"), post(2, "FACEBOOK"))
        every { metricSnapshotRepository.findLatestByCampaignPostId(1) } returns
            syncSnapshot(1, "FACEBOOK", views = 100, likes = 10, comments = 5, shares = 0)
        every { metricSnapshotRepository.findLatestByCampaignPostId(2) } returns
            syncSnapshot(2, "FACEBOOK", views = 200, likes = 20, comments = 8, shares = 0)

        val result = useCase.getAnalytics(userId, workspaceId, campaignId)

        assertNull(result.totalShares, "0 은 '공유 0회' 라는 성과 보고가 된다")
        assertEquals(0, result.measuredPostCounts[MetricSnapshot.SHARES])
        assertEquals(300, result.totalViews, "측정 가능한 지표까지 죽이면 안 된다")
    }

    /**
     * Pinterest 는 **댓글도 공유도** 주지 않는다. 플랫폼마다 빠지는 지표가 다르다.
     *
     * 공유 자리에 있던 값은 `PinterestClient.kt:160` 의 `metrics["PIN_CLICK"]` 이다.
     * PIN_CLICK 은 핀을 **클릭한 횟수**이지 공유가 아니다 — 하드코딩 0 이 아니라
     * **다른 이름의 지표를 공유로 매핑**한 경우라, 큰 숫자가 조용히 공유 합계에 더해졌다.
     */
    @Test
    @DisplayName("플랫폼마다 다른 미수집 지표를 각각 반영한다")
    fun perPlatformUnavailabilityIsRespected() {
        grantAccess()
        every { campaignPostRepository.findByCampaignId(campaignId) } returns
            listOf(post(1, "PINTEREST"), post(2, "YOUTUBE"))
        every { metricSnapshotRepository.findLatestByCampaignPostId(1) } returns
            syncSnapshot(1, "PINTEREST", views = 100, likes = 10, comments = 0, shares = 4)
        every { metricSnapshotRepository.findLatestByCampaignPostId(2) } returns
            syncSnapshot(2, "YOUTUBE", views = 50, likes = 5, comments = 7, shares = 2)

        val result = useCase.getAnalytics(userId, workspaceId, campaignId)

        assertEquals(7, result.totalComments, "Pinterest 의 미수집 댓글 0 이 섞였다")
        assertEquals(2, result.totalShares, "Pinterest 의 클릭 수가 공유 합계에 섞였다")
        // 조회수는 두 플랫폼 모두 수집한다.
        assertEquals(150, result.totalViews)
    }

    // ── 수동 백필은 살린다 ───────────────────────────────────────────────────

    /**
     * **플랫폼 이름으로 걸렀다면 이 값이 사라진다.** 운영자가 Facebook 게시물의 공유 수를
     * 손으로 확인해 넣었다면 그것은 관측이다.
     */
    @Test
    @DisplayName("수동 입력은 플랫폼이 안 주는 지표여도 합산한다")
    fun manualBackfillCountsEvenOnUnsupportedPlatform() {
        grantAccess()
        every { campaignPostRepository.findByCampaignId(campaignId) } returns listOf(post(1, "FACEBOOK"))
        every { metricSnapshotRepository.findLatestByCampaignPostId(1) } returns MetricSnapshot(
            campaignPostId = 1,
            capturedAt = LocalDateTime.of(2026, 8, 12, 0, 0),
            views = 100, likes = 10, comments = 5, shares = 42,
            source = MetricSnapshotSource.MANUAL,
        )

        val result = useCase.getAnalytics(userId, workspaceId, campaignId)

        assertEquals(42, result.totalShares, "수동 백필이 플랫폼 이름 때문에 버려졌다")
    }

    /** 사람이 0 이라고 적었으면 그것은 **측정 결과**다. */
    @Test
    @DisplayName("수동 입력의 0은 측정값으로 남는다")
    fun manualZeroIsMeasured() {
        grantAccess()
        every { campaignPostRepository.findByCampaignId(campaignId) } returns listOf(post(1, "FACEBOOK"))
        every { metricSnapshotRepository.findLatestByCampaignPostId(1) } returns MetricSnapshot(
            campaignPostId = 1,
            capturedAt = LocalDateTime.of(2026, 8, 12, 0, 0),
            views = 100, likes = 10, comments = 5, shares = 0,
            source = MetricSnapshotSource.MANUAL,
        )

        val result = useCase.getAnalytics(userId, workspaceId, campaignId)

        assertEquals(0, result.totalShares)
        assertEquals(1, result.measuredPostCounts[MetricSnapshot.SHARES])
    }

    @Test
    @DisplayName("수동 기록은 MANUAL 출처로 저장된다")
    fun recordMetricStoresManualSource() {
        grantAccess()
        every { campaignPostRepository.findById(1) } returns post(1, "FACEBOOK")
        val saved = slot<MetricSnapshot>()
        every { metricSnapshotRepository.save(capture(saved)) } answers { saved.captured.copy(id = 1) }

        val response = useCase.recordMetric(
            userId, workspaceId, 1,
            RecordMetricRequest(views = 10, likes = 2, comments = 1, shares = 0),
        )

        assertEquals(MetricSnapshotSource.MANUAL, saved.captured.source)
        assertTrue(saved.captured.unavailableMetrics.isEmpty())
        assertEquals(0, response.shares, "사람이 적은 0 이 측정 불가로 바뀌었다")
        assertTrue(response.unavailableMetrics.isEmpty())
    }

    // ── 스냅샷 없음 / 레거시 ─────────────────────────────────────────────────

    /** 스냅샷이 아예 없는 것과 "0 을 측정했다" 는 다르다. */
    @Test
    @DisplayName("스냅샷이 없으면 네 지표 모두 측정 불가다")
    fun missingSnapshotIsNotZero() {
        grantAccess()
        every { campaignPostRepository.findByCampaignId(campaignId) } returns listOf(post(1, "YOUTUBE"))
        every { metricSnapshotRepository.findLatestByCampaignPostId(1) } returns null

        val result = useCase.getAnalytics(userId, workspaceId, campaignId)

        val postMetric = result.posts.single()
        assertNull(postMetric.views)
        assertNull(postMetric.shares)
        assertEquals(MetricSnapshot.ALL_METRICS, postMetric.unavailableMetrics)
        assertNull(result.totalViews)
        assertNull(result.lastSyncedAt)
    }

    /**
     * V110 이전 행은 출처를 모른다. **0 만 미측정으로 본다** — 0 이 아닌 값은 누군가
     * 실제로 관측한 것이 분명하므로 살린다. 전부 버리면 과거 성과가 통째로 사라진다.
     */
    @Test
    @DisplayName("출처 미상 레거시 행은 0만 버리고 실제 값은 살린다")
    fun legacyUnknownSourceKeepsNonZeroValues() {
        grantAccess()
        every { campaignPostRepository.findByCampaignId(campaignId) } returns listOf(post(1, "FACEBOOK"))
        every { metricSnapshotRepository.findLatestByCampaignPostId(1) } returns MetricSnapshot(
            campaignPostId = 1,
            capturedAt = LocalDateTime.of(2026, 8, 1, 0, 0),
            views = 500, likes = 30, comments = 4, shares = 0,
            source = MetricSnapshotSource.UNKNOWN,
        )

        val result = useCase.getAnalytics(userId, workspaceId, campaignId)

        assertEquals(500, result.totalViews, "실제 관측된 값을 버렸다")
        assertEquals(30, result.totalLikes)
        assertNull(result.totalShares, "출처를 모르는 0 을 측정값으로 더했다")
    }

    // ── 게시물별 응답 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("게시물 응답이 어떤 지표를 못 쟀는지 알려 준다")
    fun postResponseReportsUnavailableMetrics() {
        grantAccess()
        every { campaignPostRepository.findByCampaignId(campaignId) } returns listOf(post(1, "FACEBOOK"))
        every { metricSnapshotRepository.findLatestByCampaignPostId(1) } returns
            syncSnapshot(1, "FACEBOOK", views = 100, likes = 10, comments = 5, shares = 0)

        val postMetric = useCase.getAnalytics(userId, workspaceId, campaignId).posts.single()

        assertEquals(100, postMetric.views)
        assertNull(postMetric.shares, "Facebook 미수집 공유가 0 으로 나갔다")
        assertEquals(listOf(MetricSnapshot.SHARES), postMetric.unavailableMetrics)
    }

    // ── 스케줄러가 미측정 목록을 채운다 ─────────────────────────────────────

    /**
     * **가용성이 저장 시점에 확정돼야 한다.**
     *
     * 조회 시점에 플랫폼 이름으로 다시 판정하면 수동 백필까지 함께 버려진다. 스케줄러가
     * 자기가 무엇을 못 물어봤는지 스냅샷에 적는다.
     */
    @Test
    @DisplayName("동기화 스냅샷은 플랫폼이 안 주는 지표를 미측정으로 표시한다")
    fun schedulerMarksUnavailableMetrics() {
        val saved = mutableListOf<MetricSnapshot>()
        val requestedRanges = mutableListOf<Pair<LocalDate, LocalDate>>()
        val scheduler = schedulerSavingInto(saved, requestedRanges)

        scheduler.syncMetrics()

        val facebook = saved.single { it.campaignPostId == 2L }
        assertEquals(MetricSnapshotSource.PLATFORM_SYNC, facebook.source)
        assertEquals(setOf(MetricSnapshot.SHARES), facebook.unavailableMetrics)

        val youtube = saved.single { it.campaignPostId == 1L }
        assertTrue(youtube.unavailableMetrics.isEmpty(), "YouTube 는 네 지표를 모두 준다")
        // 값 자체는 버리지 않는다. 나중에 플랫폼이 지원을 시작하면 되짚을 근거가 된다.
        assertEquals(7, facebook.shares)

        // 게시물 생성일부터 오늘까지 조회해야 최신 스냅샷이 누적 성과가 된다.
        assertTrue(
            requestedRanges.contains(LocalDate.of(2026, 8, 1) to LocalDate.now()),
            "게시물 생성일부터 오늘까지의 분석 범위를 요청하지 않았다: $requestedRanges",
        )
    }

    private fun schedulerSavingInto(
        saved: MutableList<MetricSnapshot>,
        requestedRanges: MutableList<Pair<LocalDate, LocalDate>> = mutableListOf(),
    ): CampaignMetricsSyncScheduler {
        val postRepo = mockk<CampaignPostRepository>()
        val snapshotRepo = mockk<MetricSnapshotRepository>()
        val channelRepo = mockk<com.ongo.domain.channel.ChannelRepository>()
        val clientPort = mockk<com.ongo.domain.channel.PlatformClientPort>()
        val tokenPort = mockk<com.ongo.domain.channel.TokenEncryptionPort>()
        val guard = mockk<com.ongo.domain.accountdeletion.UserWriteGuard>(relaxed = true)
        val lock = mockk<com.ongo.domain.lock.DistributedLockPort>()

        every { postRepo.findAll() } returns listOf(
            post(1, "YOUTUBE").copy(
                platformPostId = "yt-1",
                status = PostStatus.PUBLISHED,
                createdAt = LocalDateTime.of(2026, 8, 1, 0, 0),
            ),
            post(2, "FACEBOOK").copy(platformPostId = "fb-1", status = PostStatus.PUBLISHED),
        )
        every { channelRepo.findByUserIdAndPlatform(any(), any()) } answers {
            com.ongo.domain.channel.Channel(
                id = 1, userId = 100, platform = secondArg(), channelName = "c",
                platformChannelId = "pc", accessToken = com.ongo.domain.channel.EncryptedToken("enc"),
            )
        }
        every { tokenPort.decrypt(any()) } returns com.ongo.domain.channel.PlainToken("token")
        every {
            clientPort.getVideoAnalytics(any(), any(), any(), any(), any())
        } answers {
            requestedRanges += (args[3] as LocalDate) to (args[4] as LocalDate)
            com.ongo.domain.channel.PlatformAnalyticsResult(
                views = 100, likes = 10, comments = 5, shares = 7,
                watchTimeSeconds = 0, subscriberGained = 0, impressions = 0, avgViewDurationSeconds = 0,
            )
        }
        every { snapshotRepo.save(any()) } answers { firstArg<MetricSnapshot>().also { saved += it } }
        every { lock.withLock(any(), any<() -> Unit>()) } answers {
            secondArg<() -> Unit>().invoke()
            true
        }

        return CampaignMetricsSyncScheduler(postRepo, snapshotRepo, channelRepo, clientPort, tokenPort, guard, lock)
    }
}
