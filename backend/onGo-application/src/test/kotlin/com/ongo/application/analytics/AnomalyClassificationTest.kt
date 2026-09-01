package com.ongo.application.analytics

import com.ongo.common.enums.Platform
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.AnomalyType
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * 이상 **유형 분류**가 미수집 0 을 섞지 않는지 고정한다.
 *
 * ## 두 단계가 서로 다른 근거를 쓴다
 *
 * - **이상 여부**: `overallScore` 의 z-score. 이미 지원 플랫폼 행으로만 계산된다.
 * - **유형 분류**: 아래 비율. **이쪽만 필터 없이 전체 행을 더하고 있었다.**
 *
 * ```
 * val totalViews = videoAnalytics.sumOf { it.views }
 * val totalShares = videoAnalytics.sumOf { it.shares }
 * val totalEngagement = videoAnalytics.sumOf { it.likes + it.commentsCount + it.shares }
 * ```
 *
 * Facebook·WordPress·Vimeo 는 공유를, Pinterest 는 댓글을 주지 않는다. 그 하드코딩 0 이
 * 분자에 들어가고 그 행의 조회수는 분모에 남았다. **오차는 항상 희석 방향**이라 임계값
 * (공유 10%, 참여 20%)을 넘지 못하고 구체적 분류가 일반 `VIRAL_SPIKE` 로 떨어졌다 —
 * 공유로 퍼진 영상이 "바이럴" 로만 보고됐다.
 */
class AnomalyClassificationTest {

    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val videoRepository = mockk<VideoRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>()

    private val useCase = PerformanceScoreUseCase(
        analyticsRepository, videoRepository, videoUploadRepository,
    )

    private val userId = 7L
    private val targetVideoId = 42L
    private val targetUploadId = 101L

    private fun row(
        uploadId: Long,
        day: Int,
        views: Int,
        likes: Int = 0,
        comments: Int = 0,
        shares: Int = 0,
    ) = AnalyticsDaily(
        videoUploadId = uploadId,
        date = LocalDate.of(2026, 8, day),
        views = views,
        likes = likes,
        commentsCount = comments,
        shares = shares,
    )

    /**
     * 급등이 z-score 임계값(2.0)을 넘으려면 **비슷한 비교 대상이 여럿** 있어야 한다.
     * 동일한 저성과 영상 5개를 두면 최고 점수 영상의 z 는 약 2.24 가 된다.
     */
    private val peerUploadIds = listOf(201L, 202L, 203L, 204L, 205L)

    private fun peerRows() = peerUploadIds.flatMap { id ->
        List(4) { row(id, 20 + it, views = 10, likes = 0, shares = 0) }
    }

    /**
     * @param targetRows 대상 영상의 집계 행
     * @param platforms 대상 업로드별 플랫폼(기본 YouTube)
     * @param peerPlatform 비교군 플랫폼.
     *
     * **비교군은 대상과 같은 축 집합으로 측정돼야 한다.** 축 집합이 다르면
     * `comparablePeers` 가 걸러내 비교 대상이 0 이 되고, z-score 자체가 계산되지 않아
     * 이상이 감지되지 않는다 — 그러면 분류를 시험할 수 없다.
     */
    private fun given(
        targetRows: List<AnalyticsDaily>,
        platforms: Map<Long, Platform> = emptyMap(),
        peerPlatform: Platform = Platform.YOUTUBE,
    ) {
        val targetUploadIds = targetRows.map { it.videoUploadId }.distinct()
        fun uploadFor(id: Long) = VideoUpload(
            id = id,
            videoId = if (id in targetUploadIds) targetVideoId else 1_000L + id,
            platform = if (id in targetUploadIds) (platforms[id] ?: Platform.YOUTUBE) else peerPlatform,
            channelId = 1L,
        )

        val allUploads = (targetUploadIds + peerUploadIds).map { uploadFor(it) }
        val allRows = targetRows + peerRows()

        every { videoUploadRepository.findByUserId(userId) } returns allUploads
        every { analyticsRepository.findAllByUserId(userId) } returns allRows

        allUploads.map { it.videoId }.distinct().forEach { vid ->
            every { videoRepository.findById(vid) } returns Video(id = vid, userId = userId, title = "v$vid")
            val uploadsForVideo = allUploads.filter { it.videoId == vid }
            every { videoUploadRepository.findByVideoId(vid) } returns uploadsForVideo
            val ids = uploadsForVideo.mapNotNull { it.id }
            every { analyticsRepository.findByVideoUploadIds(ids) } returns allRows.filter { it.videoUploadId in ids }
        }
    }

    private fun anomalyTypeForTarget(): AnomalyType? =
        useCase.getAnomalies(userId).anomalies.firstOrNull { it.videoId == targetVideoId }?.anomalyType

    // ── 공유 급등 ────────────────────────────────────────────────────────────

    /** 기준선: YouTube 단독이면 공유 20% 가 그대로 잡힌다. */
    @Test
    @DisplayName("YouTube 단독 영상의 공유 급등을 공유 급등으로 분류한다")
    fun youtubeShareSpikeIsClassifiedAsShareSpike() {
        given(List(4) { row(targetUploadId, 20 + it, views = 1_000, shares = 200) })

        assertEquals(AnomalyType.SHARE_SPIKE, anomalyTypeForTarget())
    }

    /**
     * **이 케이스가 공유 급등을 "바이럴" 로 떨어뜨리던 자리다.**
     *
     * YouTube 에서 조회 1,000 당 공유 200(20%)로 퍼진 영상을 Facebook 에도 올렸다.
     * Facebook 은 공유를 주지 않으므로 그 행의 0 이 분자에, 조회수 9,000 이 분모에
     * 들어가면 비율이 2% 로 떨어져 임계값 10% 를 넘지 못한다.
     */
    @Test
    @DisplayName("Facebook 의 공유 0이 공유 급등 판정을 희석하지 않는다")
    fun facebookZeroSharesDoNotDiluteShareSpike() {
        given(
            targetRows = List(4) { row(targetUploadId, 20 + it, views = 1_000, shares = 200) } +
                List(4) { row(301L, 20 + it, views = 9_000, shares = 0) },
            platforms = mapOf(targetUploadId to Platform.YOUTUBE, 301L to Platform.FACEBOOK),
        )

        assertEquals(
            AnomalyType.SHARE_SPIKE, anomalyTypeForTarget(),
            "Facebook 의 미수집 0이 공유 비율을 희석했다",
        )
    }

    /** Facebook 단독이면 공유를 물어볼 곳이 없다 — 공유 급등을 주장하지 않는다. */
    @Test
    @DisplayName("공유를 수집하지 않는 플랫폼뿐이면 공유 급등으로 분류하지 않는다")
    fun facebookOnlyVideoIsNotClassifiedAsShareSpike() {
        given(
            targetRows = List(4) { row(targetUploadId, 20 + it, views = 1_000, likes = 10, shares = 500) },
            platforms = mapOf(targetUploadId to Platform.FACEBOOK),
            // 축 집합이 같아야 비교가 성립한다. 비교군도 Facebook 으로 둔다.
            peerPlatform = Platform.FACEBOOK,
        )

        val type = anomalyTypeForTarget()
        // **정확한 유형까지 못 박는다.** `!= SHARE_SPIKE` 만 보면 이상이 아예 감지되지
        // 않아 null 인 경우에도 통과해 버린다.
        assertEquals(
            AnomalyType.VIRAL_SPIKE, type,
            "수집하지 않는 공유 수로 공유 급등을 주장했다",
        )
    }

    // ── 참여 급등 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("YouTube 단독 영상의 참여 급등을 참여 급등으로 분류한다")
    fun youtubeEngagementSurgeIsClassified() {
        given(List(4) { row(targetUploadId, 20 + it, views = 1_000, likes = 250, comments = 50, shares = 50) })

        assertEquals(AnomalyType.ENGAGEMENT_SURGE, anomalyTypeForTarget())
    }

    /**
     * **참여율은 좋아요 + 댓글 + 공유 전체가 정의다.**
     *
     * Pinterest 는 댓글을 주지 않으므로 그 행의 합계는 정의대로 계산된 값이 아니다.
     * 좋아요만으로 임계값을 넘겼다고 "참여 급등"이라 부르면, 재지 않은 댓글을 0 으로
     * 단정한 판정이 된다.
     */
    @Test
    @DisplayName("Pinterest 는 댓글을 주지 않아 참여 급등 판정에서 제외한다")
    fun pinterestRowsAreExcludedFromEngagementClassification() {
        given(
            targetRows = List(4) { row(targetUploadId, 20 + it, views = 1_000, likes = 300, comments = 0, shares = 0) },
            platforms = mapOf(targetUploadId to Platform.PINTEREST),
            peerPlatform = Platform.PINTEREST,
        )

        val type = anomalyTypeForTarget()
        assertEquals(
            AnomalyType.VIRAL_SPIKE, type,
            "댓글이 빠진 합계로 참여 급등을 주장했다",
        )
    }

    /**
     * 혼합 영상에서 Pinterest 조회수가 참여 분모에 남으면 안 된다.
     *
     * YouTube 행만 보면 350/1,000 = 35% → 참여 급등. Pinterest 조회수 9,000 이 분모에
     * 남으면 3.5% 로 떨어져 임계값 20% 를 넘지 못한다.
     */
    @Test
    @DisplayName("혼합 영상에서 Pinterest 조회수가 참여 분모에 남지 않는다")
    fun pinterestViewsDoNotStayInEngagementDenominator() {
        given(
            targetRows = List(4) { row(targetUploadId, 20 + it, views = 1_000, likes = 250, comments = 50, shares = 50) } +
                List(4) { row(302L, 20 + it, views = 9_000, likes = 0, comments = 0, shares = 0) },
            platforms = mapOf(targetUploadId to Platform.YOUTUBE, 302L to Platform.PINTEREST),
        )

        assertEquals(
            AnomalyType.ENGAGEMENT_SURGE, anomalyTypeForTarget(),
            "Pinterest 조회수가 참여 분모에 남았다",
        )
    }

    // ── 기본 분류 ────────────────────────────────────────────────────────────

    /**
     * 공유·참여 어느 쪽도 임계값을 넘지 않으면 `VIRAL_SPIKE` 다. 조회수는 모든 플랫폼이
     * 수집하므로 "점수가 비정상적으로 높다" 는 사실 자체는 z-score 가 이미 확인했다.
     */
    @Test
    @DisplayName("공유·참여 근거가 없으면 조회수 급등으로 분류한다")
    fun plainViewSpikeIsClassifiedAsViralSpike() {
        given(List(4) { row(targetUploadId, 20 + it, views = 1_000, likes = 10) })

        assertEquals(AnomalyType.VIRAL_SPIKE, anomalyTypeForTarget())
    }
}
