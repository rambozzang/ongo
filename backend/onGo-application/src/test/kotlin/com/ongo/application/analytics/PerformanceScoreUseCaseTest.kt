package com.ongo.application.analytics

import com.ongo.application.analytics.dto.PerformanceScoreResponse
import com.ongo.common.enums.Platform
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 성과 점수는 **측정된 것만** 말해야 한다.
 *
 * 예전에는 집계가 하나도 없어도 `overallScore = 0`, `trend = "stable"`, `prediction7d = 0` 인
 * 200 성공 응답이 내려갔다. 화면은 그것을 "0점짜리 영상, 7일 예상 조회수 0회, 안정적 추세"로
 * 그렸고 크리에이터는 성과가 나쁘다고 읽었다 — 실제로는 측정 자체가 없었다.
 */
class PerformanceScoreUseCaseTest {

    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val videoRepository = mockk<VideoRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>()

    private val useCase = PerformanceScoreUseCase(
        analyticsRepository, videoRepository, videoUploadRepository,
    )

    private val userId = 7L
    private val videoId = 42L

    private fun video() = Video(id = videoId, userId = userId, title = "테스트 영상")

    private fun upload(id: Long) = VideoUpload(
        id = id, videoId = videoId, platform = Platform.YOUTUBE, channelId = 1L,
    )

    private fun daily(uploadId: Long, date: LocalDate, views: Int) = AnalyticsDaily(
        videoUploadId = uploadId,
        date = date,
        views = views,
        likes = views / 10,
        commentsCount = 1,
        shares = 1,
        watchTimeSeconds = views.toLong() * 10,
        subscriberGained = 1,
    )

    private fun givenVideoWithUploads(vararg uploadIds: Long) {
        every { videoRepository.findById(videoId) } returns video()
        every { videoUploadRepository.findByVideoId(videoId) } returns uploadIds.map { upload(it) }
    }

    // ── 데이터가 없을 때 ──────────────────────────────────────────────────────

    /**
     * **이 테스트가 수정의 핵심이다.** 집계가 없으면 점수를 만들지 않는다.
     */
    @Test
    @DisplayName("집계가 하나도 없으면 점수를 계산하지 않고 미수집으로 표시한다")
    fun noAnalyticsIsReportedAsUnavailable() {
        givenVideoWithUploads(101L)
        every { analyticsRepository.findByVideoUploadIds(listOf(101L)) } returns emptyList()

        val response = useCase.getPerformanceScore(userId, videoId)

        assertFalse(response.dataAvailable, "집계가 없는데 점수를 계산했다")
        assertEquals(PerformanceScoreResponse.REASON_NO_ANALYTICS, response.unavailableReason)
    }

    @Test
    @DisplayName("업로드가 없으면 측정 대상 자체가 없다고 표시한다")
    fun noUploadsIsReportedAsUnavailable() {
        every { videoRepository.findById(videoId) } returns video()
        every { videoUploadRepository.findByVideoId(videoId) } returns emptyList()

        val response = useCase.getPerformanceScore(userId, videoId)

        assertFalse(response.dataAvailable)
        assertEquals(PerformanceScoreResponse.REASON_NO_UPLOADS, response.unavailableReason)
    }

    /**
     * **신규 계정에서 "상위 100%" 가 나오던 경로.**
     *
     * 모든 영상의 점수가 0 이면 `count { it <= 0 }` 이 전체 개수가 되어 백분위가 100 이 된다.
     * 아무것도 측정되지 않은 영상이 최상위 성과처럼 보였다. 이제 그 계산에 도달하지 않는다.
     */
    @Test
    @DisplayName("측정값이 없으면 백분위를 상위 100%로 보고하지 않는다")
    fun unmeasuredVideoIsNotReportedAsTopPercentile() {
        givenVideoWithUploads(101L)
        every { analyticsRepository.findByVideoUploadIds(listOf(101L)) } returns emptyList()

        val response = useCase.getPerformanceScore(userId, videoId)

        // null 이어야 한다. 0.0 은 "상위 0%"(최상위)로 읽혀 미측정이 최고 성과가 된다.
        assertEquals(null, response.percentileRank, "미측정인데 백분위를 매겼다")
        assertFalse(response.dataAvailable)
    }

    /**
     * **자리채움 숫자를 아예 두지 않는다.**
     *
     * 예전에는 `overallScore = 0.0`, `trend = "stable"`, `prediction7d = 0` 을 채우고
     * `dataAvailable = false` 로만 구분했다. 그 플래그를 놓친 소비자는 "0점 · 7일 예상
     * 0회 · 안정적 추세"를 그렸다. 값이 없으면 놓칠 값 자체가 없다.
     */
    @Test
    @DisplayName("미수집 응답에는 숫자를 채워 넣지 않는다")
    fun noPlaceholderNumbersWhenUnmeasured() {
        givenVideoWithUploads(101L)
        every { analyticsRepository.findByVideoUploadIds(listOf(101L)) } returns emptyList()

        val response = useCase.getPerformanceScore(userId, videoId)

        assertNull(response.overallScore, "0점을 지어냈다")
        assertNull(response.prediction7d, "7일 예상 조회수를 지어냈다")
        assertNull(response.trend, "관측한 적 없는 추세를 말했다")
        assertTrue(response.breakdown.values.all { it == null }, "하위 점수를 지어냈다: ${response.breakdown}")
        assertFalse(response.isAnomaly)
        assertFalse(response.dataAvailable)
    }

    // ── 데이터가 있을 때는 그대로 계산한다 ────────────────────────────────────

    @Test
    @DisplayName("집계가 있으면 점수를 계산하고 측정됨으로 표시한다")
    fun analyticsPresentProducesRealScore() {
        givenVideoWithUploads(101L)
        val rows = listOf(
            daily(101L, LocalDate.of(2026, 8, 20), 1_000),
            daily(101L, LocalDate.of(2026, 8, 21), 1_500),
            daily(101L, LocalDate.of(2026, 8, 22), 2_000),
            daily(101L, LocalDate.of(2026, 8, 23), 2_500),
        )
        every { analyticsRepository.findByVideoUploadIds(listOf(101L)) } returns rows
        every { analyticsRepository.findAllByUserId(userId) } returns rows
        every { videoUploadRepository.findByUserId(userId) } returns listOf(upload(101L))

        val response = useCase.getPerformanceScore(userId, videoId)

        assertTrue(response.dataAvailable, "집계가 있는데 미수집으로 표시했다")
        assertEquals(null, response.unavailableReason)
        assertTrue(response.overallScore!! > 0.0, "측정값이 있는데 0점이다: ${response.overallScore}")
        assertTrue(response.prediction7d!! > 0, "상승 추세인데 예상 조회수가 0이다")
    }

    // ── 순위(상위 %) ─────────────────────────────────────────────────────────
    //
    // 값은 **낮을수록 좋다**. 예전에는 "나보다 낮거나 같은 비율"이라 최고 영상이 100 을
    // 받았고, 화면은 그것을 "Top 100%" 로 찍었다 — 의미와 라벨이 반대였다.

    /** 영상 3개(업로드 1개씩)를 가진 채널을 만든다. `views` 가 클수록 점수가 높다. */
    private fun givenChannel(vararg videoViews: Pair<Long, Int>) {
        val uploads = videoViews.mapIndexed { index, (vid, _) ->
            VideoUpload(id = 100L + index, videoId = vid, platform = Platform.YOUTUBE, channelId = 1L)
        }
        val rows = videoViews.flatMapIndexed { index, (_, views) ->
            listOf(
                daily(100L + index, LocalDate.of(2026, 8, 20), views),
                daily(100L + index, LocalDate.of(2026, 8, 21), views),
            )
        }
        every { videoRepository.findById(videoId) } returns video()
        every { videoUploadRepository.findByUserId(userId) } returns uploads
        every { analyticsRepository.findAllByUserId(userId) } returns rows

        val target = uploads.first { it.videoId == videoId }
        every { videoUploadRepository.findByVideoId(videoId) } returns listOf(target)
        every { analyticsRepository.findByVideoUploadIds(listOf(target.id!!)) } returns
            rows.filter { it.videoUploadId == target.id }
    }

    @Test
    @DisplayName("최고 성과 영상이 가장 낮은 상위 %를 받는다 — Top 100% 가 아니다")
    fun bestVideoGetsSmallestTopPercent() {
        givenChannel(videoId to 100_000, 43L to 100, 44L to 50)

        val response = useCase.getPerformanceScore(userId, videoId)

        val top = response.percentileRank
        assertTrue(top != null && top <= 40.0, "최고 성과인데 상위 $top% 로 나왔다")
    }

    @Test
    @DisplayName("최저 성과 영상이 가장 높은 상위 %를 받는다")
    fun worstVideoGetsLargestTopPercent() {
        givenChannel(videoId to 10, 43L to 50_000, 44L to 100_000)

        val response = useCase.getPerformanceScore(userId, videoId)

        assertEquals(100.0, response.percentileRank, "최저 성과는 상위 100% 여야 한다")
    }

    /** 같은 점수끼리는 같은 순위다. 임의로 순서를 갈라 한쪽을 위로 올리지 않는다. */
    @Test
    @DisplayName("동점이면 함께 같은 상위 %를 받는다")
    fun tiedVideosShareTheSameRank() {
        givenChannel(videoId to 1_000, 43L to 1_000, 44L to 1_000)

        val response = useCase.getPerformanceScore(userId, videoId)

        assertEquals(100.0, response.percentileRank, "셋 다 동점이면 모두 상위 100% 다")
    }

    /**
     * 미수집 영상의 점수는 0 이다. 분모에 넣으면 **남이 측정되지 않았다는 사실이 내 순위**가
     * 된다 — 채널에 신규 영상이 늘수록 내 순위가 좋아진다.
     */
    @Test
    @DisplayName("집계가 없는 영상은 순위 분모에서 뺀다")
    fun unmeasuredPeersAreExcludedFromTheDenominator() {
        val measured = VideoUpload(id = 100L, videoId = videoId, platform = Platform.YOUTUBE, channelId = 1L)
        val alsoMeasured = VideoUpload(id = 101L, videoId = 43L, platform = Platform.YOUTUBE, channelId = 1L)
        // 업로드는 있으나 집계 행이 없는 영상 셋.
        val unmeasured = (0..2).map {
            VideoUpload(id = 200L + it, videoId = 50L + it, platform = Platform.YOUTUBE, channelId = 1L)
        }
        val rows = listOf(
            daily(100L, LocalDate.of(2026, 8, 20), 10),
            daily(101L, LocalDate.of(2026, 8, 20), 100_000),
        )
        every { videoRepository.findById(videoId) } returns video()
        every { videoUploadRepository.findByUserId(userId) } returns
            listOf(measured, alsoMeasured) + unmeasured
        every { analyticsRepository.findAllByUserId(userId) } returns rows
        every { videoUploadRepository.findByVideoId(videoId) } returns listOf(measured)
        every { analyticsRepository.findByVideoUploadIds(listOf(100L)) } returns
            rows.filter { it.videoUploadId == 100L }

        val response = useCase.getPerformanceScore(userId, videoId)

        // 측정된 영상은 2개뿐이고 이 영상이 더 낮다 → 상위 100%.
        // 미수집 3개가 분모에 들어갔다면 2/5 = 40% 처럼 좋아 보였을 것이다.
        assertEquals(100.0, response.percentileRank)
    }

    /** 자기 자신뿐이면 순위는 언제나 "상위 100%" 라 정보가 아니다. */
    @Test
    @DisplayName("비교할 영상이 자기 자신뿐이면 순위를 매기지 않는다")
    fun singleVideoHasNoRank() {
        givenChannel(videoId to 4_200)

        val response = useCase.getPerformanceScore(userId, videoId)

        assertEquals(null, response.percentileRank, "비교 대상이 없는데 순위를 만들었다")
        assertTrue(response.dataAvailable, "측정은 됐으므로 점수 자체는 있다")
    }

    /**
     * **척도가 같아야 비교가 성립한다.**
     *
     * 예전에는 요청 영상만 5개 지표 가중식으로 계산하고, 비교 대상은 전혀 다른 2개 지표
     * 공식으로 계산했다. 같은 데이터를 넣어도 두 값이 달라 순위가 무의미했다.
     * 모든 영상이 동일하면 순위는 반드시 100%(전원 동점)여야 한다.
     */
    @Test
    @DisplayName("모든 영상이 동일하면 척도 차이로 순위가 갈리지 않는다")
    fun identicalVideosAreRankedOnTheSameScale() {
        givenChannel(videoId to 5_000, 43L to 5_000)

        val response = useCase.getPerformanceScore(userId, videoId)

        assertEquals(100.0, response.percentileRank, "동일한 영상인데 순위가 갈렸다 — 척도가 다르다")
    }

    /**
     * 예전에는 영상마다 `findByVideoId` + `findByVideoUploadIds` 를 불러 영상 수만큼
     * 쿼리가 나갔다(N+1 두 겹). 순위 계산은 이미 가져온 데이터만 써야 한다.
     */
    @Test
    @DisplayName("순위 계산이 영상 수만큼 추가 조회를 하지 않는다")
    fun rankingDoesNotIssuePerVideoQueries() {
        givenChannel(videoId to 1_000, 43L to 2_000, 44L to 3_000)

        useCase.getPerformanceScore(userId, videoId)

        // 요청한 영상 한 건에 대해서만 조회한다.
        verify(exactly = 1) { videoUploadRepository.findByVideoId(any()) }
        verify(exactly = 1) { analyticsRepository.findByVideoUploadIds(any()) }
    }

    // ── 이상 감지도 같은 척도로 비교한다 ─────────────────────────────────────
    //
    // z-score 는 비교군의 평균·표준편차로 계산한다. 내 점수와 비교군의 척도가 다르면
    // 임계값 2.0 이 아무것도 뜻하지 않는다 — 정상 영상이 "바이럴"로 뜨거나 진짜 급등을 놓친다.

    /**
     * **모두 동일한 영상이면 이상은 없다.**
     *
     * 예전에는 내 점수(5개 지표 가중식)와 비교군(조회수/참여율 2개 지표)의 척도가 달라,
     * 완전히 같은 데이터인데도 z-score 가 0 이 아니었다. 이 테스트는 그 불일치가
     * 되살아나면 곧바로 깨진다.
     */
    @Test
    @DisplayName("모든 영상이 동일하면 이상으로 감지하지 않는다 — 척도가 같다는 증거")
    fun identicalVideosProduceNoAnomaly() {
        givenChannel(videoId to 5_000, 43L to 5_000, 44L to 5_000, 45L to 5_000)

        val response = useCase.getPerformanceScore(userId, videoId)

        assertFalse(response.isAnomaly, "동일한 영상들 사이에서 이상이 감지됐다 — 척도가 어긋났다")
        assertEquals(null, response.anomalyDescription)
    }

    /**
     * **비교군은 영상 단위여야 한다.**
     *
     * 예전 이상 감지는 `groupBy { it.videoUploadId }` 로 묶어 **업로드 하나를 영상 하나로**
     * 셌다. 그런데 내 점수는 그 영상의 모든 업로드를 합친 값이다. 3개 플랫폼에 올린 영상은
     * 비교군에서 1/3 짜리 셋으로 쪼개져 합쳐진 내 점수가 늘 높아 보였다.
     *
     * 여기서는 모든 영상이 2개 플랫폼에 동일하게 올라가 있다. 영상 단위로 묶으면 전원
     * 동점이라 이상이 없고 순위도 100% 다. 업로드 단위로 쪼개면 그 관계가 깨진다.
     */
    @Test
    @DisplayName("여러 플랫폼에 올린 영상을 비교군에서 쪼개지 않는다")
    fun multiPlatformVideoCountsAsOnePeer() {
        val uploads = listOf(
            VideoUpload(id = 100L, videoId = videoId, platform = Platform.YOUTUBE, channelId = 1L),
            VideoUpload(id = 101L, videoId = videoId, platform = Platform.TIKTOK, channelId = 2L),
            VideoUpload(id = 102L, videoId = 43L, platform = Platform.YOUTUBE, channelId = 1L),
            VideoUpload(id = 103L, videoId = 43L, platform = Platform.TIKTOK, channelId = 2L),
            VideoUpload(id = 104L, videoId = 44L, platform = Platform.YOUTUBE, channelId = 1L),
            VideoUpload(id = 105L, videoId = 44L, platform = Platform.TIKTOK, channelId = 2L),
        )
        val rows = uploads.map { daily(it.id!!, LocalDate.of(2026, 8, 20), 3_000) }

        every { videoRepository.findById(videoId) } returns video()
        every { videoUploadRepository.findByUserId(userId) } returns uploads
        every { analyticsRepository.findAllByUserId(userId) } returns rows
        every { videoUploadRepository.findByVideoId(videoId) } returns uploads.filter { it.videoId == videoId }
        every { analyticsRepository.findByVideoUploadIds(listOf(100L, 101L)) } returns
            rows.filter { it.videoUploadId in listOf(100L, 101L) }

        val response = useCase.getPerformanceScore(userId, videoId)

        // 영상 3개가 전부 동점 → 상위 100%, 이상 없음.
        assertEquals(100.0, response.percentileRank, "비교군이 업로드 단위로 쪼개졌다")
        assertFalse(response.isAnomaly)
    }

    /**
     * 순위와 이상 감지가 **같은 모집단**을 봐야 한다. 한쪽만 미수집 영상을 포함하면
     * 두 값이 서로 다른 사실을 말하게 된다.
     */
    @Test
    @DisplayName("순위와 이상 감지가 같은 모집단을 쓴다")
    fun rankAndAnomalyShareThePopulation() {
        val measuredUploads = (0..3).map {
            VideoUpload(id = 100L + it, videoId = if (it == 0) videoId else 43L + it, platform = Platform.YOUTUBE, channelId = 1L)
        }
        // 집계가 없는 영상 — 어느 쪽 모집단에도 들어가면 안 된다.
        val unmeasured = VideoUpload(id = 200L, videoId = 90L, platform = Platform.YOUTUBE, channelId = 1L)
        val rows = measuredUploads.map { daily(it.id!!, LocalDate.of(2026, 8, 20), 5_000) }

        every { videoRepository.findById(videoId) } returns video()
        every { videoUploadRepository.findByUserId(userId) } returns measuredUploads + unmeasured
        every { analyticsRepository.findAllByUserId(userId) } returns rows
        every { videoUploadRepository.findByVideoId(videoId) } returns listOf(measuredUploads.first())
        every { analyticsRepository.findByVideoUploadIds(listOf(100L)) } returns
            rows.filter { it.videoUploadId == 100L }

        val response = useCase.getPerformanceScore(userId, videoId)

        // 측정된 4개가 전부 동점이다. 미수집이 섞였다면 동점이 깨져 둘 다 달라진다.
        assertEquals(100.0, response.percentileRank)
        assertFalse(response.isAnomaly, "미수집 영상이 이상 감지 모집단에 섞였다")
    }

    /**
     * 실제로 0회 조회된 영상은 **측정된 0** 이다. 미수집과 달리 그대로 보여줘야 한다.
     * 이 구분이 없으면 이번 수정이 "0점을 전부 숨기는" 것이 되어 버린다.
     */
    @Test
    @DisplayName("실제로 조회수가 0인 영상은 측정됨으로 표시한다 — 미수집과 다르다")
    fun genuinelyZeroViewsIsStillMeasured() {
        givenVideoWithUploads(101L)
        val rows = listOf(daily(101L, LocalDate.of(2026, 8, 20), 0))
        every { analyticsRepository.findByVideoUploadIds(listOf(101L)) } returns rows
        every { analyticsRepository.findAllByUserId(userId) } returns rows
        every { videoUploadRepository.findByUserId(userId) } returns listOf(upload(101L))

        val response = useCase.getPerformanceScore(userId, videoId)

        assertTrue(response.dataAvailable, "집계 행이 있으면 값이 0이어도 측정된 것이다")
    }
}
